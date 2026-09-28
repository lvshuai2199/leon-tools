package springboot.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import springboot.domain.SysMenus;
import springboot.domain.SysUsers;
import springboot.service.RegCodeAccessService;
import springboot.service.SysMenusService;
import springboot.utils.ApiResponse;
import springboot.utils.ForbiddenException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理端接口 /admin/** 的角色校验，排在 {@link AuthInterceptor}（token 校验）之后。
 * <ol>
 *   <li>子账号、注册码客户：一律 403（他们只能用用户端）</li>
 *   <li>ROOT：放行</li>
 *   <li>其他角色：按 {@link AdminAccessRules} 取第一条匹配规则，看角色是否拥有对应菜单；未登记的路径只允许 ROOT</li>
 * </ol>
 * 拒绝时返回 HTTP 403，body 为 {@code {status: 403, message, data: null}}。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String WEB_BLOCKED_MESSAGE = "当前账号不能访问管理端";

    private final RegCodeAccessService regCodeAccessService;
    private final SysMenusService sysMenusService;
    private final JsonMapper jsonMapper;

    public AdminAuthInterceptor(RegCodeAccessService regCodeAccessService, SysMenusService sysMenusService,
                                JsonMapper jsonMapper) {
        this.regCodeAccessService = regCodeAccessService;
        this.sysMenusService = sysMenusService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (AdminAccessRules.isSuspicious(request.getRequestURI())) {
            return deny(response, ForbiddenException.DEFAULT_MESSAGE);
        }
        String path = AdminAccessRules.normalize(request.getServletPath(), request.getPathInfo(),
                request.getRequestURI(), request.getContextPath());
        SysUsers user = regCodeAccessService.currentUser(request);
        if (user == null) {
            // 正常情况下 AuthInterceptor 已经挡住；这里兜底按无权限处理
            return deny(response, ForbiddenException.DEFAULT_MESSAGE);
        }
        if (regCodeAccessService.isWebBlocked(user)) {
            return deny(response, WEB_BLOCKED_MESSAGE);
        }
        if (regCodeAccessService.isRootUser(user)) {
            return true;
        }
        AdminAccessRules.Rule rule = AdminAccessRules.match(request.getMethod(), path);
        List<String> menuIds = regCodeAccessService.menuIdsOf(user);
        if (menuIds == null) {
            menuIds = Collections.emptyList();
        }
        Set<String> components = rule.kind() == AdminAccessRules.Kind.COMPONENT
                ? componentsOf(menuIds) : Collections.emptySet();
        if (rule.allows(menuIds, components)) {
            return true;
        }
        return deny(response, ForbiddenException.DEFAULT_MESSAGE);
    }

    private Set<String> componentsOf(List<String> menuIds) {
        if (menuIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<SysMenus> menus = sysMenusService.listByIds(menuIds);
        if (menus == null) {
            return Collections.emptySet();
        }
        return menus.stream()
                .filter(Objects::nonNull)
                .map(SysMenus::getComponent)
                .filter(Objects::nonNull)
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    private boolean deny(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(
                ApiResponse.withStatus(ForbiddenException.FORBIDDEN_STATUS, message, null)));
        return false;
    }
}
