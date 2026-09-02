package springboot.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import springboot.utils.ApiResponse;

/**
 * 未捕获异常转为统一 JSON，便于前端提示，并让操作日志过滤器记录 ERROR。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ApiResponse handle(Exception e, HttpServletRequest request) {
        log.error("未捕获异常 {} {}", request.getMethod(), request.getRequestURI(), e);
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = e.getClass().getSimpleName();
        }
        request.setAttribute(OperationLogFilter.ATTR_ERROR_STACK, stackHint(e));
        return ApiResponse.failure(msg);
    }

    private static String stackHint(Throwable e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.toString());
        StackTraceElement[] els = e.getStackTrace();
        int n = Math.min(els == null ? 0 : els.length, 12);
        for (int i = 0; i < n; i++) {
            sb.append("\n  at ").append(els[i]);
        }
        return sb.toString();
    }
}
