package springboot.utils;

/**
 * 已登录但无权限：由 {@link springboot.config.GlobalExceptionHandler} 转成
 * HTTP 403 + {@code {status: 403, message, data: null}}，与 401（未登录 / 登录失效）区分开。
 */
public class ForbiddenException extends RuntimeException {

    public static final int FORBIDDEN_STATUS = 403;
    public static final String DEFAULT_MESSAGE = "无权限访问";

    public ForbiddenException() {
        super(DEFAULT_MESSAGE);
    }

    public ForbiddenException(String message) {
        super(message == null || message.isBlank() ? DEFAULT_MESSAGE : message);
    }
}
