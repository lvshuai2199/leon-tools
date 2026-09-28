package springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import springboot.DTO.RegCodeQuotaVO;
import springboot.domain.RegCodeUser;
import springboot.domain.RegCodeUserConfig;
import springboot.domain.SysMenus;
import springboot.domain.SysUsers;
import springboot.utils.ForbiddenException;
import springboot.utils.RequestUserUtils;
import springboot.utils.RoleUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 注册码权限：管理员可用全部配置且不限次数；客户仅能使用分配的配置并受配额约束。
 */
@Service
public class RegCodeAccessService {

    public static final String MENU_REGCODE_USER = "menu_regcode_user";
    public static final String MENU_REGCODE_CONFIG = "menu_regcode_config";
    public static final String MENU_REGCODE = "menu_regcode";
    public static final String MENU_CRAB = "menu_crab";
    public static final String ROLE_REGCODE_CLIENT_ID = "role_regcode_client";

    private final SysUsersService sysUsersService;
    private final SysRolesService sysRolesService;
    private final SysRoleMenuService sysRoleMenuService;
    private final SysMenusService sysMenusService;
    private final RegCodeUserService regCodeUserService;
    private final RegCodeUserConfigService regCodeUserConfigService;

    public RegCodeAccessService(SysUsersService sysUsersService,
                                SysRolesService sysRolesService,
                                SysRoleMenuService sysRoleMenuService,
                                SysMenusService sysMenusService,
                                RegCodeUserService regCodeUserService,
                                RegCodeUserConfigService regCodeUserConfigService) {
        this.sysUsersService = sysUsersService;
        this.sysRolesService = sysRolesService;
        this.sysRoleMenuService = sysRoleMenuService;
        this.sysMenusService = sysMenusService;
        this.regCodeUserService = regCodeUserService;
        this.regCodeUserConfigService = regCodeUserConfigService;
    }

    public boolean isRootUser(String userId) {
        return isRootUser(userId == null || userId.isBlank() ? null : sysUsersService.getById(userId));
    }

    public boolean isRootUser(SysUsers user) {
        if (user == null) {
            return false;
        }
        if (RoleUtils.isRoot(user.getRoleId(), user.getRoleName())) {
            return true;
        }
        if (user.getRoleId() == null || user.getRoleId().isBlank()) {
            return false;
        }
        return RoleUtils.isRoot(sysRolesService.getById(user.getRoleId()));
    }

    public SysUsers findUser(String userId, String username) {
        if (userId != null && !userId.isBlank()) {
            SysUsers byId = sysUsersService.getById(userId.trim());
            if (byId != null) {
                return byId;
            }
        }
        if (username == null || username.isBlank()) {
            return null;
        }
        return pickPreferredUser(listByUsername(username.trim()));
    }

    public List<SysUsers> listByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<SysUsers> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUsers::getUsername, username.trim());
        List<SysUsers> users = sysUsersService.list(wrapper);
        return users == null ? Collections.emptyList() : users;
    }

    /** 同名账号优先用能管理注册码的那条，避免 getOne 抽到客户影子号 */
    public SysUsers pickPreferredUser(List<SysUsers> users) {
        if (users == null || users.isEmpty()) {
            return null;
        }
        if (users.size() == 1) {
            return users.get(0);
        }
        return users.stream()
                .min(Comparator
                        .comparing((SysUsers user) -> isRootUser(user) ? 0 : 1)
                        .thenComparing(user -> hasRegCodeAdminMenus(user) ? 0 : 1)
                        .thenComparing(user -> canLoginWeb(user) ? 0 : 1)
                        .thenComparing(user -> ROLE_REGCODE_CLIENT_ID.equals(user.getRoleId()) ? 1 : 0))
                .orElse(users.get(0));
    }

    /** 手机端（用户端）登录：任何有效账号都可以登录，能用哪些功能再按下面的规则判断 */
    public boolean canLoginMobile(SysUsers user) {
        return user != null;
    }

    /** 出货：注册码客户角色不能用；其他账号（含普通子账号）可以用，数据范围另行限制 */
    public boolean canUseCrab(SysUsers user) {
        return user != null && !isRegCodeUser(user);
    }

    /**
     * 注册码生成（/common/**）：只允许
     * ① 角色为 role_regcode_client 的注册码用户；② ROOT（及 {@link #adminMayGenerate} 放行的管理员）；
     * ③ 子账号且其 parent_id 指向的用户是注册码用户或 ROOT（每次实时查父用户，不缓存）。
     */
    public boolean canUseRegCode(SysUsers user) {
        if (user == null) {
            return false;
        }
        if (isRegCodeUser(user) || adminMayGenerate(user)) {
            return true;
        }
        if (!isSubAccount(user)) {
            return false;
        }
        SysUsers parent = sysUsersService.getById(user.getParentId().trim());
        return parent != null && (isRegCodeUser(parent) || isRootUser(parent));
    }

    /**
     * 哪些“管理员”可以直接生成注册码（包括不选配置、按类型生成）。
     * 目前只有 ROOT；以后要放宽给其他管理员，只改这一处（例如加上 {@code || isManager(user)}）。
     */
    public boolean adminMayGenerate(SysUsers user) {
        return isRootUser(user);
    }

    /** 当前登录人必须能用注册码生成，否则 403；返回当前用户 */
    public SysUsers requireRegCode(HttpServletRequest request) {
        SysUsers user = currentUser(request);
        if (user == null) {
            throw new ForbiddenException("请先登录");
        }
        if (!canUseRegCode(user)) {
            throw new ForbiddenException("无权使用注册码生成");
        }
        return user;
    }

    /** 当前登录人必须能用出货，否则 403；返回当前用户 */
    public SysUsers requireCrab(HttpServletRequest request) {
        SysUsers user = currentUser(request);
        if (user == null) {
            throw new ForbiddenException("请先登录");
        }
        if (!canUseCrab(user)) {
            throw new ForbiddenException("无螃蟹出货权限");
        }
        return user;
    }

    public List<String> menuIdsOf(SysUsers user) {
        if (user == null || user.getRoleId() == null || user.getRoleId().isBlank()) {
            return Collections.emptyList();
        }
        if (isRootUser(user)) {
            return null;
        }
        List<String> menuIds = sysRoleMenuService.getMenuIdsByRole(user.getRoleId());
        return menuIds == null ? Collections.emptyList() : menuIds;
    }

    public boolean hasMenu(SysUsers user, String menuId) {
        if (user == null || menuId == null || menuId.isBlank()) {
            return false;
        }
        if (isRootUser(user)) {
            return true;
        }
        List<String> menuIds = menuIdsOf(user);
        return menuIds != null && menuIds.contains(menuId);
    }

    /** Web 管理端：子账号和注册码客户都不能登录，也不能调 /admin/** */
    public boolean canLoginWeb(SysUsers user) {
        return user != null && !isWebBlocked(user);
    }

    /** 管理端要挡掉的账号：子账号（parent_id 非空）或注册码客户角色 */
    public boolean isWebBlocked(SysUsers user) {
        return user != null && (isSubAccount(user) || isRegCodeUser(user));
    }

    /** 注册码用户：只看角色是否为 role_regcode_client（不再把“挂了父用户”当成注册码用户） */
    public boolean isRegCodeUser(SysUsers user) {
        return user != null && user.getRoleId() != null
                && ROLE_REGCODE_CLIENT_ID.equals(user.getRoleId().trim());
    }

    /** 子账号：parent_id 非空 */
    public boolean isSubAccount(SysUsers user) {
        return user != null && user.getParentId() != null && !user.getParentId().isBlank();
    }

    /** 当前登录用户：只按 token 校验后得到的 userId 查找，不再按用户名兜底 */
    public SysUsers currentUser(HttpServletRequest request) {
        return findUser(RequestUserUtils.currentUserId(request), null);
    }

    public boolean isManager(String userId) {
        return isManager(findUser(userId, null));
    }

    public boolean isManager(SysUsers user) {
        if (user == null) {
            return false;
        }
        if (isRootUser(user)) {
            return true;
        }
        return hasRegCodeAdminMenus(user);
    }

    private boolean hasRegCodeAdminMenus(SysUsers user) {
        if (user == null || user.getRoleId() == null || user.getRoleId().isBlank()) {
            return false;
        }
        List<String> menuIds = sysRoleMenuService.getMenuIdsByRole(user.getRoleId());
        if (menuIds == null || menuIds.isEmpty()) {
            return false;
        }
        Set<String> owned = new HashSet<>(menuIds);
        if (owned.contains(MENU_REGCODE_USER) || owned.contains(MENU_REGCODE_CONFIG)) {
            return true;
        }
        LambdaQueryWrapper<SysMenus> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
                .like(SysMenus::getMenuName, "注册码配置")
                .or().like(SysMenus::getMenuName, "注册码用户")
                .or().like(SysMenus::getComponent, "regcode-config")
                .or().like(SysMenus::getComponent, "regcode-user")
                .or().like(SysMenus::getMenuUrl, "regcode-config")
                .or().like(SysMenus::getMenuUrl, "regcode-user")
                .or().eq(SysMenus::getId, MENU_REGCODE_USER)
                .or().eq(SysMenus::getId, MENU_REGCODE_CONFIG));
        return sysMenusService.list(wrapper).stream()
                .anyMatch(menu -> menu != null && owned.contains(menu.getId()));
    }

    public String requireManager(HttpServletRequest request) {
        SysUsers user = currentUser(request);
        if (user == null) {
            return "请先登录";
        }
        if (!isManager(user)) {
            return "无权限管理注册码用户或配置";
        }
        return null;
    }

    public String requireManager(String userId) {
        return requireManager(userId, null);
    }

    public String requireManager(String userId, String username) {
        SysUsers user = findUser(userId, username);
        if (user == null) {
            return "请先登录";
        }
        if (!isManager(user)) {
            return "无权限管理注册码用户或配置";
        }
        return null;
    }

    public RegCodeUser getAssignment(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<RegCodeUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RegCodeUser::getUserId, userId).last("LIMIT 1");
        return regCodeUserService.getOne(wrapper, false);
    }

    public List<String> listAssignedConfigIds(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<RegCodeUserConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RegCodeUserConfig::getUserId, userId);
        return regCodeUserConfigService.list(wrapper).stream()
                .map(RegCodeUserConfig::getConfigId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());
    }

    /**
     * @return null 表示管理员可用全部；空列表表示无权；否则仅这些配置
     */
    public List<String> allowedConfigIds(String userId) {
        return allowedConfigIds(userId == null || userId.isBlank() ? null : sysUsersService.getById(userId));
    }

    public List<String> allowedConfigIds(SysUsers user) {
        if (isRootUser(user)) {
            return null;
        }
        String userId = user == null ? null : user.getId();
        if (isRootUser(userId)) {
            return null;
        }
        if (getAssignment(userId) != null) {
            return listAssignedConfigIds(userId);
        }
        return null;
    }

    public String assertCanGenerate(String userId, String configId) {
        if (userId == null || userId.isBlank()) {
            return "请先登录";
        }
        if (isRootUser(userId)) {
            return null;
        }
        RegCodeUser assignment = getAssignment(userId);
        if (assignment != null) {
            if (configId == null || configId.isBlank()) {
                return "请选择注册码配置";
            }
            List<String> configIds = listAssignedConfigIds(userId);
            if (!configIds.contains(configId)) {
                return "无权使用该注册码配置";
            }
            int used = assignment.getGenerateUsed() == null ? 0 : assignment.getGenerateUsed();
            int limit = assignment.getGenerateLimit() == null ? 0 : assignment.getGenerateLimit();
            if (used >= limit) {
                return "生成次数已用完";
            }
            return null;
        }
        return null;
    }

    public RegCodeQuotaVO quotaOf(String userId) {
        RegCodeQuotaVO vo = new RegCodeQuotaVO();
        RegCodeUser assignment = getAssignment(userId);
        if (assignment != null) {
            vo.setUnlimited(false);
            int used = assignment.getGenerateUsed() == null ? 0 : assignment.getGenerateUsed();
            int limit = assignment.getGenerateLimit() == null ? 0 : assignment.getGenerateLimit();
            vo.setGenerateUsed(used);
            vo.setGenerateLimit(limit);
            vo.setRemaining(Math.max(limit - used, 0));
            return vo;
        }
        vo.setUnlimited(true);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void consumeQuota(String userId) {
        RegCodeUser assignment = getAssignment(userId);
        if (assignment == null) {
            return;
        }
        int used = assignment.getGenerateUsed() == null ? 0 : assignment.getGenerateUsed();
        assignment.setGenerateUsed(used + 1);
        assignment.setUpdateTime(springboot.utils.DateUtils.getNow());
        regCodeUserService.updateById(assignment);
    }

    @Transactional(rollbackFor = Exception.class)
    public void replaceConfigs(String userId, List<String> configIds) {
        LambdaQueryWrapper<RegCodeUserConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RegCodeUserConfig::getUserId, userId);
        regCodeUserConfigService.remove(wrapper);
        if (configIds == null || configIds.isEmpty()) {
            return;
        }
        List<RegCodeUserConfig> rows = configIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .map(configId -> {
                    RegCodeUserConfig row = new RegCodeUserConfig();
                    row.setUserId(userId);
                    row.setConfigId(configId);
                    return row;
                })
                .collect(Collectors.toList());
        if (!rows.isEmpty()) {
            regCodeUserConfigService.saveBatch(rows);
        }
    }
}
