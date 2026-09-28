package springboot.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前登录用户：只读取 AuthInterceptor 校验 token 后写入的 request attribute。
 * 不再读取客户端传来的 X-User-Id / X-Username 请求头（可被伪造）。
 */
public final class RequestUserUtils {

    public static final String ATTR_USER_ID = "leon.auth.userId";
    public static final String ATTR_USERNAME = "leon.auth.username";
    public static final String ATTR_TOKEN = "leon.auth.token";

    private RequestUserUtils() {
    }

    public static String currentUserId(HttpServletRequest request) {
        return attribute(request, ATTR_USER_ID);
    }

    public static String currentUsername(HttpServletRequest request) {
        return attribute(request, ATTR_USERNAME);
    }

    public static String currentToken(HttpServletRequest request) {
        return attribute(request, ATTR_TOKEN);
    }

    private static String attribute(HttpServletRequest request, String name) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(name);
        if (value instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return null;
    }
}
