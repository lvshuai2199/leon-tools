package springboot.service.cursor;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 用服务器上的 CURSOR_KEY_SECRET 加密用户自己的 Cursor API Key。密钥不落日志、不回给前端。
 */
public final class CursorKeyCipher {

    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final String secret;

    public CursorKeyCipher(String secret) {
        this.secret = secret == null ? "" : secret;
    }

    public boolean ready() {
        return !secret.isBlank();
    }

    public String seal(String plain) {
        requireReady();
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(enc, 0, packed, iv.length, enc.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (Exception e) {
            throw new IllegalStateException("加密 Cursor Key 失败");
        }
    }

    public String open(String packed) {
        requireReady();
        try {
            byte[] raw = Base64.getDecoder().decode(packed);
            if (raw.length <= IV_LEN) {
                throw new IllegalArgumentException("cipher too short");
            }
            byte[] iv = new byte[IV_LEN];
            byte[] enc = new byte[raw.length - IV_LEN];
            System.arraycopy(raw, 0, iv, 0, IV_LEN);
            System.arraycopy(raw, IV_LEN, enc, 0, enc.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(enc), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("无法解密 Cursor Key");
        }
    }

    private void requireReady() {
        if (!ready()) {
            throw new IllegalStateException("未配置 CURSOR_KEY_SECRET");
        }
    }

    private SecretKeySpec key() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
