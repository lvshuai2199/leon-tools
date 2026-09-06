package springboot.utils;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestUserUtils {

    private RequestUserUtils() {
    }

    public static String currentUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return userId.trim();
        }
        return null;
    }
}
