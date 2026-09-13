package springboot.utils;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * 注册码哈希：默认 MD5，与历史生成结果保持一致。
 */
public class HashUtil {

    public static String hash(String input, String algorithm) {
        String alg = normalize(algorithm);
        if ("MD5".equals(alg)) {
            return MD5Util.hash(input);
        }
        try {
            MessageDigest md = MessageDigest.getInstance(alg);
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("不支持的加密方式: " + algorithm, e);
        }
    }

    /**
     * 与 charge-tool 的 utils::hmacSha256 保持一致：
     * HMAC-SHA256(message=input, key=secretKey)，输出小写十六进制字符串。
     */
    public static String hmacSha256(String input, String secretKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 计算失败", e);
        }
    }

    private static String normalize(String algorithm) {
        if (algorithm == null || algorithm.isBlank()) {
            return "MD5";
        }
        String a = algorithm.trim().toUpperCase(Locale.ROOT);
        if ("SHA256".equals(a) || "SHA-256".equals(a)) {
            return "SHA-256";
        }
        if ("SHA1".equals(a) || "SHA-1".equals(a)) {
            return "SHA-1";
        }
        return a;
    }
}
