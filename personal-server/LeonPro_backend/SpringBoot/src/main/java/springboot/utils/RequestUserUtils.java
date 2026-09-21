package springboot.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import springboot.domain.SysUsers;
import springboot.service.SysUsersService;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Component
public class RequestUserUtils {

    private static RequestUserUtils instance;

    private final SysUsersService sysUsersService;

    public RequestUserUtils(SysUsersService sysUsersService) {
        this.sysUsersService = sysUsersService;
        instance = this;
    }

    public static String currentUserId(HttpServletRequest request) {
        if (instance != null) {
            return instance.resolve(request);
        }
        return headerValue(request, "X-User-Id");
    }

    public static String currentUsername(HttpServletRequest request) {
        return decode(headerValue(request, "X-Username"));
    }

    private String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String userId = headerValue(request, "X-User-Id");
        if (userId != null && this.sysUsersService.getById(userId) != null) {
            return userId;
        }
        String username = currentUsername(request);
        if (username != null) {
            LambdaQueryWrapper<SysUsers> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysUsers::getUsername, username);
            SysUsers user = this.sysUsersService.getOne(wrapper, false);
            if (user != null) {
                return user.getId();
            }
        }
        return userId;
    }

    private static String headerValue(HttpServletRequest request, String name) {
        if (request == null) {
            return null;
        }
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String decode(String value) {
        if (value == null) {
            return null;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return value;
        }
    }
}
