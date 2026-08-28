package springboot.utils;

/**
 * 操作人员记录：有用户 ID 则原样保存，否则记为「未知人员」。
 */
public final class OperatorUtils {

    public static final String UNKNOWN = "未知人员";

    private OperatorUtils() {
    }

    public static String resolve(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return UNKNOWN;
        }
        return userId.trim();
    }
}
