package springboot.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 登录凭证：随机不透明 token，存 Redis（key 只含 token 的 SHA-256，不存明文）。
 * key = leonpro:{profile}:auth:token:{sha256(token)} -> userId，TTL 7 天，每次校验通过后续期。
 * 直接用 StringRedisTemplate，不经 RedisUtil（它会把 value 打到 info 日志）。
 */
@Service
public class AuthTokenService {

    public static final Duration TTL = Duration.ofDays(7);
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

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
