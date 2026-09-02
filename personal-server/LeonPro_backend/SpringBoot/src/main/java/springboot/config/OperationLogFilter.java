package springboot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import springboot.domain.SysOperationLog;
import springboot.service.SysOperationLogService;
import springboot.utils.OperatorUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 记录写操作、登录、业务失败与未捕获错误，供后期回溯与崩溃排查。
 * 成功的纯查询（GET/HEAD）不入库，避免刷屏。
 */
@Slf4j
@Component
public class OperationLogFilter extends OncePerRequestFilter implements Ordered {

    public static final String ATTR_ERROR_STACK = "leon.oplog.errorStack";

    private static final int MAX_TEXT = 4000;
    private static final int CACHE_LIMIT = 64 * 1024;
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(\"(?:password|captchaCode|imageBase64|markdown)\"\\s*:\\s*\")[^\"]*");

    private final SysOperationLogService sysOperationLogService;
    private final JsonMapper jsonMapper;

    public OperationLogFilter(SysOperationLogService sysOperationLogService, JsonMapper jsonMapper) {
        this.sysOperationLogService = sysOperationLogService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 20;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (shouldNotFilterInner(request)) {
            chain.doFilter(request, response);
            return;
        }
        ContentCachingRequestWrapper req = request instanceof ContentCachingRequestWrapper
                ? (ContentCachingRequestWrapper) request
                : new ContentCachingRequestWrapper(request, CACHE_LIMIT);
        ContentCachingResponseWrapper res = response instanceof ContentCachingResponseWrapper
                ? (ContentCachingResponseWrapper) response
                : new ContentCachingResponseWrapper(response);
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(req, res);
        } finally {
            persist(req, res, start);
            res.copyBodyToResponse();
        }
    }

    private boolean shouldNotFilterInner(HttpServletRequest request) {
        String uri = path(request);
        if (uri.startsWith("/public/") || uri.startsWith("/error")) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return uri.contains("/auth/captcha");
    }

    private void persist(ContentCachingRequestWrapper req, ContentCachingResponseWrapper res, long start) {
        try {
            String uri = path(req);
            String method = req.getMethod();
            boolean mutating = !("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method));
            int httpStatus = res.getStatus();
            ParsedBody parsed = parseResponse(res);

            boolean failed = httpStatus >= 400
                    || (parsed.bizStatus != null && parsed.bizStatus != 200)
                    || req.getAttribute(ATTR_ERROR_STACK) != null;
            if (isSelfQuery(uri, method) && !failed) {
                return;
            }
            if (!mutating && !failed) {
                return;
            }

            SysOperationLog record = new SysOperationLog();
            record.setOperatorId(header(req, "X-User-Id"));
            record.setOperatorName(resolveOperator(req));
            record.setModule(resolveModule(uri));
            record.setAction(method + " " + uri);
            record.setRequestMethod(method);
            record.setRequestUri(uri);
            record.setRequestParams(buildParams(req));
            record.setIp(clientIp(req));
            record.setUserAgent(trim(req.getHeader("User-Agent"), 500));
            record.setCostMs(System.currentTimeMillis() - start);
            record.setCreateTime(new Date());

            Object stackAttr = req.getAttribute(ATTR_ERROR_STACK);
            if (stackAttr != null || httpStatus >= 500) {
                record.setStatus("ERROR");
                record.setResultMsg(firstNonBlank(parsed.message, "HTTP " + httpStatus));
                record.setErrorMsg(trim(stackAttr != null ? String.valueOf(stackAttr) : parsed.raw, MAX_TEXT));
            } else if (failed) {
                record.setStatus("FAIL");
                record.setResultMsg(firstNonBlank(parsed.message, "HTTP " + httpStatus));
                record.setErrorMsg(trim(parsed.raw, MAX_TEXT));
            } else {
                record.setStatus("SUCCESS");
                record.setResultMsg(firstNonBlank(parsed.message, "Success"));
            }
            sysOperationLogService.saveQuietly(record);
        } catch (Exception e) {
            log.warn("记录操作日志时出错: {}", e.getMessage());
        }
    }

    private ParsedBody parseResponse(ContentCachingResponseWrapper res) {
        ParsedBody parsed = new ParsedBody();
        byte[] body = res.getContentAsByteArray();
        if (body == null || body.length == 0) {
            return parsed;
        }
        String contentType = res.getContentType();
        if (contentType != null && !contentType.contains(MediaType.APPLICATION_JSON_VALUE)
                && !contentType.contains("json")) {
            return parsed;
        }
        String raw = new String(body, StandardCharsets.UTF_8);
        parsed.raw = raw;
        try {
            JsonNode node = jsonMapper.readTree(raw);
            if (node != null && node.has("status")) {
                parsed.bizStatus = node.get("status").asInt();
            }
            if (node != null && node.has("message") && !node.get("message").isNull()) {
                parsed.message = node.get("message").asString();
            }
        } catch (Exception ignored) {
            // 非 JSON 响应忽略
        }
        return parsed;
    }

    private String buildParams(ContentCachingRequestWrapper req) {
        StringBuilder sb = new StringBuilder();
        String qs = req.getQueryString();
        if (qs != null && !qs.isBlank()) {
            sb.append("query=").append(qs);
        }
        byte[] cached = req.getContentAsByteArray();
        if (cached != null && cached.length > 0) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append("body=").append(new String(cached, StandardCharsets.UTF_8));
        }
        return trim(SECRET.matcher(sb.toString()).replaceAll("$1***"), MAX_TEXT);
    }

    private String resolveOperator(HttpServletRequest req) {
        String name = decodeHeader(req.getHeader("X-Username"));
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        String userId = header(req, "X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        String fromQuery = req.getParameter("username");
        if (fromQuery != null && !fromQuery.isBlank()) {
            return fromQuery.trim();
        }
        String bodyUser = usernameFromBody(req);
        if (bodyUser != null) {
            return bodyUser;
        }
        return OperatorUtils.UNKNOWN;
    }

    private String usernameFromBody(HttpServletRequest req) {
        if (!(req instanceof ContentCachingRequestWrapper)) {
            return null;
        }
        ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) req;
        byte[] cached = wrapper.getContentAsByteArray();
        if (cached == null || cached.length == 0) {
            return null;
        }
        try {
            JsonNode node = jsonMapper.readTree(new String(cached, StandardCharsets.UTF_8));
            if (node != null && node.has("username") && !node.get("username").isNull()) {
                String u = node.get("username").asString();
                if (u != null && !u.isBlank()) {
                    return u.trim();
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static String resolveModule(String uri) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("/auth", "认证");
        map.put("/sysUsers", "用户管理");
        map.put("/sysRoles", "角色管理");
        map.put("/sysMenus", "路由配置");
        map.put("/sysOperationLog", "操作日志");
        map.put("/comRegistration", "注册码记录");
        map.put("/regCodeConfig", "注册码配置");
        map.put("/mindmap", "思维导图");
        map.put("/sysTasks", "任务管理");
        map.put("/systemData", "系统数据");
        map.put("/extern", "外部接口");
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (uri.startsWith(e.getKey()) || uri.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return "其他";
    }

    private static boolean isSelfQuery(String uri, String method) {
        return "GET".equalsIgnoreCase(method) && uri.contains("/sysOperationLog");
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        if (uri.isEmpty()) {
            return "/";
        }
        return uri;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String header(HttpServletRequest request, String name) {
        String v = request.getHeader(name);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String decodeHeader(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return raw;
        }
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "...";
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static class ParsedBody {
        Integer bizStatus;
        String message;
        String raw;
    }
}
