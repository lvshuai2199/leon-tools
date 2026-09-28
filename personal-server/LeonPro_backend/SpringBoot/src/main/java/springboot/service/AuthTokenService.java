package springboot.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 登录凭证：随机不透明 token，存 Redis（key 只含 token 的 SHA-256，不存明文）。
 * key = leonpro:{profile}:auth:token:{sha256(token)} -> userId，TTL 7 天，每次校验通过后续期。
 * 直接用 StringRedisTemplate，不经 RedisUtil（它会把 value 打到 info 日志）。
 */
@Slf4j
@Service
public class AuthTokenService {

    public static final Duration TTL = Duration.ofDays(7);
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SCAN_BATCH = 500;

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public AuthTokenService(StringRedisTemplate redis, Environment environment) {
        this.redis = redis;
        String[] profiles = environment.getActiveProfiles();
        String profile = profiles.length == 0 ? "default" : String.join("-", profiles);
        this.keyPrefix = "leonpro:" + profile + ":auth:token:";
    }

    /** 签发新 token 并写入 Redis，返回明文 token（只在登录响应里出现一次） */
    public String issue(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        byte[] buf = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(buf);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        redis.opsForValue().set(key(token), userId, TTL);
        return token;
    }

    /** 校验 token，有效则续期并返回 userId；无效/过期/已吊销返回 null */
    public String resolve(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return null;
        }
        String key = key(token.trim());
        String userId = redis.opsForValue().get(key);
        if (userId == null || userId.isBlank()) {
            return null;
        }
        redis.expire(key, TTL);
        return userId;
    }

    /** 吊销 token（登出） */
    public void revoke(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return;
        }
        redis.delete(key(token.trim()));
    }

    /**
     * 吊销某用户的全部 token（停用、重置密码、创建人被删除时调用），返回删除的数量。
     * token 的 key 只含哈希、value 是 userId，没有按用户的索引，所以用 SCAN 遍历本环境的 token key 并比对 value
     * （不用 KEYS，不阻塞 Redis）。token 数量等于在线会话数，量很小。Redis 出错时记日志并返回 -1，不影响业务操作本身。
     */
    public int revokeAllForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }
        String target = userId.trim();
        int removed = 0;
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(keyPrefix + "*").count(SCAN_BATCH).build())) {
            List<String> batch = new ArrayList<>(SCAN_BATCH);
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= SCAN_BATCH) {
                    removed += deleteOwnedBy(batch, target);
                    batch.clear();
                }
            }
            removed += deleteOwnedBy(batch, target);
        } catch (RuntimeException e) {
            log.error("吊销用户 {} 的 token 失败：{}", target, e.getMessage(), e);
            return -1;
        }
        if (removed > 0) {
            log.info("已吊销用户 {} 的 {} 个登录 token", target, removed);
        }
        return removed;
    }

    private int deleteOwnedBy(List<String> keys, String userId) {
        if (keys.isEmpty()) {
            return 0;
        }
        List<String> values = redis.opsForValue().multiGet(keys);
        List<String> owned = new ArrayList<>();
        for (int i = 0; values != null && i < keys.size() && i < values.size(); i++) {
            if (userId.equals(values.get(i))) {
                owned.add(keys.get(i));
            }
        }
        if (owned.isEmpty()) {
            return 0;
        }
        Long n = redis.delete(owned);
        return n == null ? 0 : n.intValue();
    }

    public String keyPrefix() {
        return keyPrefix;
    }

    String key(String token) {
        return keyPrefix + sha256Hex(token);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 取 Authorization: Bearer xxx 中的 token；没有或格式不对返回 null */
    public static String bearerToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String header = request.getHeader("Authorization");
        if (header == null) {
            return null;
        }
        String value = header.trim();
        if (value.length() <= 7 || !value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = value.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
