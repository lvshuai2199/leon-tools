package springboot.controller.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springboot.DTO.MeUpdateForm;
import springboot.DTO.MeVO;
import springboot.config.AuthInterceptor;
import springboot.domain.SysMenus;
import springboot.domain.SysRoles;
import springboot.domain.SysUsers;
import springboot.service.AuthTokenService;
import springboot.service.RegCodeAccessService;
import springboot.service.SysMenusService;
import springboot.service.SysRoleMenuService;
import springboot.service.SysRolesService;
import springboot.service.SysUsersService;
import springboot.utils.ApiResponse;
import springboot.utils.RequestUserUtils;
import springboot.utils.RoleUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 认证：登录、登出、当前用户、后台菜单。只有 /auth/login 免登录，其余都要 token。
 */
@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final SysUsersService sysUsersService;
    private final SysRolesService sysRolesService;
    private final SysRoleMenuService sysRoleMenuService;
    private final SysMenusService sysMenusService;
    private final RegCodeAccessService regCodeAccessService;
    private final AuthTokenService authTokenService;

    public AuthController(SysUsersService sysUsersService, SysRolesService sysRolesService,
                          SysRoleMenuService sysRoleMenuService, SysMenusService sysMenusService,
                          RegCodeAccessService regCodeAccessService, AuthTokenService authTokenService) {
        this.sysUsersService = sysUsersService;
        this.sysRolesService = sysRolesService;
        this.sysRoleMenuService = sysRoleMenuService;
        this.sysMenusService = sysMenusService;
        this.regCodeAccessService = regCodeAccessService;
        this.authTokenService = authTokenService;
    }

    /**
     * 登录（原 /auth/login2）。body: {username, password, source}；source=app/h5 走手机端校验，其他走 Web 校验
     * （子账号、注册码客户不能登录 Web）。返回用户信息 + menuIds + token。
     */
    @PostMapping("/login")
    public ApiResponse login(@RequestBody Map<String, String> loginData) {
        String username = loginData.get("username") == null ? "" : loginData.get("username").trim();
        String password = loginData.get("password") == null ? "" : loginData.get("password");

        LambdaQueryWrapper<SysUsers> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(SysUsers::getUsername, username).eq(SysUsers::getPassword, password);
        SysUsers user = this.regCodeAccessService.pickPreferredUser(this.sysUsersService.list(lambdaQueryWrapper));
        if (user == null) {
            return ApiResponse.failure("用户名或密码错误");
        }
        if (user.getRoleId() != null && !user.getRoleId().isBlank()) {
            SysRoles role = sysRolesService.getById(user.getRoleId());
            if (role != null && role.getIsDisabled() != null && role.getIsDisabled() != 0) {
                return ApiResponse.failure("该用户角色已禁用");
            }
        }
        String source = loginData.get("source");
        boolean mobile = source != null && ("app".equalsIgnoreCase(source) || "h5".equalsIgnoreCase(source));
        if (mobile) {
            if (!this.regCodeAccessService.canLoginMobile(user)) {
                return ApiResponse.failure("当前账号没有手机端可用功能，请联系管理员分配权限");
            }
        } else if (!this.regCodeAccessService.canLoginWeb(user)) {
            return ApiResponse.failure("子用户请使用手机端登录，仅可生成注册码");
        }
        fillRoleName(user);
        user.setMenuIds(this.regCodeAccessService.menuIdsOf(user));
        user.setToken(authTokenService.issue(user.getId()));
        return ApiResponse.success(user);
    }

    /** 登出：吊销当前请求携带的 token（该接口本身需登录，拦截器已校验 token） */
    @PostMapping("/logout")
    public ApiResponse logout(HttpServletRequest request) {
        String token = RequestUserUtils.currentToken(request);
        if (token == null) {
            token = AuthTokenService.bearerToken(request);
        }
        authTokenService.revoke(token);
        return ApiResponse.success(null);
    }

    /** 当前登录用户（只按 token），替代原 /sysUsers/getMyInfo */
    @GetMapping("/me")
    public ApiResponse me(HttpServletRequest request) {
        SysUsers user = this.regCodeAccessService.currentUser(request);
        if (user == null) {
            return unauthorized();
        }
        fillRoleName(user);
        MeVO vo = new MeVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setEmail(user.getEmail());
        vo.setCreateTime(user.getCreateTime());
        vo.setRoleId(user.getRoleId());
        vo.setRoleName(user.getRoleName());
        vo.setParentId(user.getParentId());
        vo.setMenuIds(this.regCodeAccessService.menuIdsOf(user));
        // 一期：sys_menus 还没有 client 字段，用户端菜单固定为空数组；二期按菜单清单同步后填充
        vo.setAppMenus(Collections.emptyList());
        vo.setRoot(this.regCodeAccessService.isRootUser(user));
        vo.setCanLoginWeb(this.regCodeAccessService.canLoginWeb(user));
        vo.setCanUseCrab(this.regCodeAccessService.canUseCrab(user));
        vo.setCanUseRegCode(this.regCodeAccessService.canUseRegCode(user));
        return ApiResponse.success(vo);
    }

    /**
     * 修改自己的资料：只认 nickname、phone、email、password 四个字段（白名单 DTO），
     * 角色、父用户、用户名等一律忽略。phone 目前 sys_users 没有对应列，接收但不保存。
     */
    @PostMapping("/me")
    public ApiResponse updateMe(@RequestBody(required = false) MeUpdateForm form, HttpServletRequest request) {
        SysUsers current = this.regCodeAccessService.currentUser(request);
        if (current == null) {
            return unauthorized();
        }
        if (form == null) {
            return ApiResponse.failure("没有要修改的内容");
        }
        SysUsers patch = new SysUsers();
        patch.setId(current.getId());
        boolean changed = false;
        if (form.getNickname() != null) {
            patch.setNickname(form.getNickname().trim());
            changed = true;
        }
        if (form.getEmail() != null) {
            String email = form.getEmail().trim();
            if (!email.isEmpty() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                return ApiResponse.failure("邮箱格式不正确");
            }
            patch.setEmail(email);
            changed = true;
        }
        if (form.getPassword() != null && !form.getPassword().isEmpty()) {
            if (form.getPassword().length() < MIN_PASSWORD_LENGTH) {
                return ApiResponse.failure("密码长度不能少于6位");
            }
            // 与后台用户管理保持一致的存储方式
            patch.setPassword(form.getPassword());
            changed = true;
        }
        if (!changed) {
            return ApiResponse.failure("没有要修改的内容");
        }
        boolean ok = this.sysUsersService.updateById(patch);
        return ok ? ApiResponse.success("保存成功") : ApiResponse.failure("保存失败");
    }

    /**
     * 后台菜单路由（原 /auth/getMenuList，去掉 username 参数，只按 token）：
     * ROOT 返回全部目录与菜单；其他角色返回已分配菜单并补全祖先目录；子账号 / 注册码客户返回空数组。
     */
    @GetMapping("/menus")
    public ApiResponse menus(HttpServletRequest request) {
        SysUsers user = this.regCodeAccessService.currentUser(request);
        if (user == null) {
            return unauthorized();
        }
        if (this.regCodeAccessService.isWebBlocked(user)) {
            return ApiResponse.success(Collections.emptyList());
        }
        if (user.getRoleId() == null || user.getRoleId().isEmpty()) {
            return ApiResponse.success(Collections.emptyList());
        }
        SysRoles role = sysRolesService.getById(user.getRoleId());
        if (RoleUtils.isRoot(role)) {
            return ApiResponse.success(listAllMenus());
        }
        List<String> menuIds = sysRoleMenuService.getMenuIdsByRole(user.getRoleId());
        if (menuIds == null || menuIds.isEmpty()) {
            return ApiResponse.success(Collections.emptyList());
        }
        Set<String> visibleIds = new HashSet<>(menuIds);
        Map<String, SysMenus> menuMap = sysMenusService.list().stream()
                .collect(Collectors.toMap(SysMenus::getId, Function.identity(), (a, b) -> a));
        for (String id : menuIds) {
            SysMenus cur = menuMap.get(id);
            while (cur != null && cur.getParentId() != null && !"0".equals(cur.getParentId())
                    && visibleIds.add(cur.getParentId())) {
                cur = menuMap.get(cur.getParentId());
            }
        }
        LambdaQueryWrapper<SysMenus> queryWrapper = new LambdaQueryWrapper<>();
        // 0目录 1菜单 2按钮：路由只取目录与菜单
        queryWrapper.ne(SysMenus::getMenuType, 2);
        queryWrapper.in(SysMenus::getId, visibleIds);
        queryWrapper.orderByAsc(SysMenus::getSortOrder);
        return ApiResponse.success(sysMenusService.list(queryWrapper));
    }

    private List<SysMenus> listAllMenus() {
        LambdaQueryWrapper<SysMenus> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.ne(SysMenus::getMenuType, 2);
        queryWrapper.orderByAsc(SysMenus::getSortOrder);
        return sysMenusService.list(queryWrapper);
    }

    private void fillRoleName(SysUsers user) {
        if (user == null || user.getRoleId() == null || user.getRoleId().isEmpty()) {
            return;
        }
        SysRoles role = sysRolesService.getById(user.getRoleId());
        if (role != null) {
            user.setRoleName(role.getRoleName());
        }
    }

    private static ApiResponse unauthorized() {
        return ApiResponse.withStatus(AuthInterceptor.UNAUTHORIZED_STATUS, AuthInterceptor.UNAUTHORIZED_MESSAGE, null);
    }
}
