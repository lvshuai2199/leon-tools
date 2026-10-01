package springboot.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import springboot.domain.SysRoles;
import springboot.domain.SysUsers;
import springboot.service.AuthTokenService;
import springboot.service.RegCodeAccessService;
import springboot.service.SysRolesService;
import springboot.service.SysUsersService;
import springboot.utils.ApiResponse;
import springboot.utils.RequestUserUtils;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * 后台接口统一登录校验：只认 Authorization: Bearer {token}（服务端 Redis 校验），
 * 由 token 推出当前用户并写入 request attribute；客户端传的 X-User-Id / X-Username 一律忽略。
 * 放行路径见 {@link AuthWebConfig#PUBLIC_PATHS}。
 * <p>
 * 以下情况一律 401（登录已失效）：没有 / 伪造 / 过期 / 已吊销的 token；token 对应的用户已删除；角色被禁用；
 * 账号被停用（reg_code_user.status = 0，此时顺手删掉这个 token）。停用账号重新登录时由登录接口返回“该账号已停用”。
 * 403 只用于“登录有效但权限被收回”（例如创建人失去注册码权限、角色没有出货菜单）。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final int UNAUTHORIZED_STATUS = 401;
    public static final String UNAUTHORIZED_MESSAGE = "登录已失效，请重新登录";

    private final AuthTokenService authTokenService;
    private final SysUsersService sysUsersService;
    private final SysRolesService sysRolesService;
    private final RegCodeAccessService regCodeAccessService;
    private final JsonMapper jsonMapper;

    public AuthInterceptor(AuthTokenService authTokenService, SysUsersService sysUsersService,
                           SysRolesService sysRolesService, RegCodeAccessService regCodeAccessService,
                           JsonMapper jsonMapper) {
        this.authTokenService = authTokenService;
        this.sysUsersService = sysUsersService;
        this.sysRolesService = sysRolesService;
        this.regCodeAccessService = regCodeAccessService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = AuthTokenService.bearerToken(request);
        String userId = token == null ? null : authTokenService.resolve(token);
        SysUsers user = userId == null ? null : sysUsersService.getById(userId);
        if (user == null || isRoleDisabled(user)) {
            writeUnauthorized(response);
            return false;
        }
        if (regCodeAccessService.isRegCodeDisabled(user)) {
            // 停用账号的 token 作废（停用时已批量吊销，这里兜底处理其它途径停用的情况）
            authTokenService.revoke(token);
            writeUnauthorized(response);
            return false;
        }
        request.setAttribute(RequestUserUtils.ATTR_USER_ID, user.getId());
        request.setAttribute(RequestUserUtils.ATTR_USERNAME, user.getUsername());
        request.setAttribute(RequestUserUtils.ATTR_TOKEN, token);
        return true;
    }

    private boolean isRoleDisabled(SysUsers user) {
        if (user.getRoleId() == null || user.getRoleId().isBlank()) {
            return false;
        }
        SysRoles role = sysRolesService.getById(user.getRoleId());
        return role != null && role.getIsDisabled() != null && role.getIsDisabled() != 0;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("WWW-Authenticate", "Bearer");
        response.getWriter().write(jsonMapper.writeValueAsString(
                ApiResponse.withStatus(UNAUTHORIZED_STATUS, UNAUTHORIZED_MESSAGE, null)));
    }
}
