package springboot.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import springboot.utils.ApiResponse;
import springboot.utils.ForbiddenException;

/**
 * 未捕获异常转为统一 JSON，便于前端提示，并让操作日志过滤器记录 ERROR。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 静态资源不存在（如已删除 / 已移动的壁纸文件）返回真实的 HTTP 404，
     * 避免被下面的兜底处理成 200 + JSON，也不记 ERROR 堆栈。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.withStatus(404, "资源不存在", null));
    }

    /** 已登录但无权限：HTTP 403 + {status: 403, message}，前端据此提示“无权限”，不要当成登录失效 */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.withStatus(ForbiddenException.FORBIDDEN_STATUS, e.getMessage(), null));
    }

    /** 上传超过 spring.servlet.multipart 限制：返回业务提示（status 413），不当作 500 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse handleMaxUpload(MaxUploadSizeExceededException e) {
        return ApiResponse.withStatus(413, "单张图片不能超过 20MB", null);
    }

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
