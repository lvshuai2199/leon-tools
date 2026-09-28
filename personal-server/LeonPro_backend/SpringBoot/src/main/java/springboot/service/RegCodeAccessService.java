package springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import springboot.DTO.RegCodeQuotaVO;
import springboot.DTO.RegCodeSubUser;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 注册码权限与次数查询：ROOT 可用全部配置且不限次数；其他账号只能用分配了次数的配置，按配置分别计次。
 * 次数的扣减 / 分配 / 退回都在 {@link RegCodeQuotaService} 里用事务 + 条件更新完成。
 */
@Service
public class RegCodeAccessService {

    public static final String MENU_REGCODE_USER = "menu_regcode_user";
    public static final String MENU_REGCODE_CONFIG = "menu_regcode_config";
    public static final String MENU_REGCODE = "menu_regcode";
    public static final String MENU_CRAB = "menu_crab";
    public static final String ROLE_REGCODE_CLIENT_ID = "role_regcode_client";
    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 0;

    private final SysUsersService sysUsersService;
    private final SysRolesService sysRolesService;
    private final SysRoleMenuService sysRoleMenuService;
    private final SysMenusService sysMenusService;
    private final RegCodeUserService regCodeUserService;
    private final RegCodeUserConfigService regCodeUserConfigService;
    private final RegCodeConfigService regCodeConfigService;

    /** 用户端出货菜单的路由（与菜单同步的首次授权用同一个配置） */
    @Value("${app.menu-sync.first-grant.app-crab-route:/crab}")
    private String appCrabRoute = "/crab";

    public RegCodeAccessService(SysUsersService sysUsersService,
                                SysRolesService sysRolesService,
                                SysRoleMenuService sysRoleMenuService,
                                SysMenusService sysMenusService,
                                RegCodeUserService regCodeUserService,
                                RegCodeUserConfigService regCodeUserConfigService,
                                RegCodeConfigService regCodeConfigService) {
        this.sysUsersService = sysUsersService;
        this.sysRolesService = sysRolesService;
        this.sysRoleMenuService = sysRoleMenuService;
        this.sysMenusService = sysMenusService;
        this.regCodeUserService = regCodeUserService;
        this.regCodeUserConfigService = regCodeUserConfigService;
        this.regCodeConfigService = regCodeConfigService;
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

    /**
     * 用户端螃蟹出货（/app/crabShipment/**）的访问规则，每次请求实时计算（与注册码同样的“跟着创建人走”模式）：
     * <ol>
     *   <li>ROOT：可以</li>
     *   <li>注册码客户角色（role_regcode_client）：永远不可以（自己或创建人是这个角色都不行）</li>
     *   <li>账号被停用（reg_code_user.status = 0）：不可以</li>
     *   <li>顶层账号（parent_id 为空）：看自己的角色有没有“出货菜单”</li>
     *   <li>子账号：看创建人（parent_id 指向的账号）——创建人存在、是顶层账号、角色未禁用、账号未停用、
     *       角色有“出货菜单”，才可以；创建人是 ROOT 也可以。子账号自己的角色不参与判断</li>
     * </ol>
     * “出货菜单”：菜单同步后是用户端（client=app）的出货菜单（路由见 app.menu-sync.first-grant.app-crab-route，
     * 默认 /crab）；该菜单已停用则谁都不能用（ROOT 除外）；库里还没有用户端出货菜单（清单没同步 / 没有新字段）时，
     * 退回看原来的管理端 menu_crab。
     */
    public boolean canUseCrab(SysUsers user) {
        if (user == null) {
            return false;
        }
        if (isRootUser(user)) {
            return true;
        }
        if (isRegCodeUser(user) || isRegCodeDisabled(user)) {
            return false;
        }
        String crabMenuId = effectiveCrabMenuId();
        if (crabMenuId == null) {
            return false;
        }
        if (!isSubAccount(user)) {
            return roleHasMenu(user.getRoleId(), crabMenuId);
        }
        SysUsers creator = sysUsersService.getById(user.getParentId().trim());
        if (creator == null) {
            return false;
        }
        if (isRootUser(creator)) {
            return !isRoleDisabled(creator);
        }
        if (isSubAccount(creator) || isRegCodeUser(creator) || isRoleDisabled(creator) || isRegCodeDisabled(creator)) {
            return false;
        }
        return roleHasMenu(creator.getRoleId(), crabMenuId);
    }

    /**
     * 判断用户端出货权限时要看的菜单 id：
     * 有用户端出货菜单且未停用 → 它的 id；有但已停用 → null（谁都没有）；没有 → 管理端 menu_crab（同步前的兜底）。
     */
    public String effectiveCrabMenuId() {
        SysMenus appCrab = findAppCrabMenu();
        if (appCrab == null) {
            return MENU_CRAB;
        }
        return appCrab.getDisabled() != null && appCrab.getDisabled() != 0 ? null : appCrab.getId();
    }

    private SysMenus findAppCrabMenu() {
        try {
            List<SysMenus> rows = sysMenusService.list(new LambdaQueryWrapper<SysMenus>()
                    .eq(SysMenus::getClient, "app")
                    .eq(SysMenus::getRouteKey, appCrabRoute)
                    .last("LIMIT 1"));
            return rows == null || rows.isEmpty() ? null : rows.get(0);
        } catch (RuntimeException e) {
            // 老库还没有 client / route_key 字段（SchemaPatcher 未能补列）时按“没有用户端菜单”处理
            return null;
        }
    }

    private boolean roleHasMenu(String roleId, String menuId) {
        if (roleId == null || roleId.isBlank() || menuId == null) {
            return false;
        }
        List<String> menuIds = sysRoleMenuService.getMenuIdsByRole(roleId.trim());
        return menuIds != null && menuIds.contains(menuId);
    }

    /**
     * 注册码生成（/common/**）的访问规则，每次请求都实时计算（父用户、角色、状态都不缓存）：
     * <ol>
     *   <li>ROOT：可以</li>
     *   <li>自己的注册码账号被停用（reg_code_user.status = 0）：不可以</li>
     *   <li>顶层账号（parent_id 为空，或父用户是 ROOT / 管理端账号，如管理员在后台建的注册码客户）：
     *       看自己角色是否分配了“注册码生成”菜单（menu_regcode）</li>
     *   <li>底层子用户（在注册码页由客户创建，父用户是注册码客户）：看创建人——创建人存在、角色未禁用、
     *       注册码账号未停用、自己是顶层账号且角色有注册码生成菜单，才可以；否则下一次请求就 403</li>
     * </ol>
     */
    public boolean canUseRegCode(SysUsers user) {
        if (user == null) {
            return false;
        }
        if (isRootUser(user)) {
            return true;
        }
        if (isRegCodeDisabled(user)) {
            return false;
        }
        if (!isSubAccount(user)) {
            return roleHasRegCode(user);
        }
        SysUsers parent = sysUsersService.getById(user.getParentId().trim());
        if (parent == null) {
            return false;
        }
        if (isAdminAccount(parent)) {
            return roleHasRegCode(user);
        }
        // 底层子用户：权限跟着创建人走（只允许一层，创建人自己必须是顶层账号）
        return creatorGrantsAccess(parent);
    }

    /** 创建人（顶层客户）是否仍然有效且有注册码权限 */
    private boolean creatorGrantsAccess(SysUsers creator) {
        if (creator == null || isRoleDisabled(creator) || isRegCodeDisabled(creator)) {
            return false;
        }
        if (isRootUser(creator)) {
            return true;
        }
        if (isSubAccount(creator)) {
            SysUsers grand = sysUsersService.getById(creator.getParentId().trim());
            if (grand == null || !isAdminAccount(grand)) {
                return false;
            }
        }
        return roleHasRegCode(creator);
    }

    /** ROOT 或能登录管理端的账号（非子账号、非注册码客户角色） */
    public boolean isAdminAccount(SysUsers user) {
        return user != null && (isRootUser(user) || !isWebBlocked(user));
    }

    /** 角色是否分配了“注册码生成”菜单 */
    public boolean roleHasRegCode(SysUsers user) {
        if (user == null || user.getRoleId() == null || user.getRoleId().isBlank()) {
            return false;
        }
        List<String> menuIds = sysRoleMenuService.getMenuIdsByRole(user.getRoleId());
        return menuIds != null && menuIds.contains(MENU_REGCODE);
    }

    private boolean isRoleDisabled(SysUsers user) {
        if (user.getRoleId() == null || user.getRoleId().isBlank()) {
            return false;
        }
        springboot.domain.SysRoles role = sysRolesService.getById(user.getRoleId());
        return role != null && role.getIsDisabled() != null && role.getIsDisabled() != 0;
    }

    /** 注册码账号被停用（有 reg_code_user 记录且 status = 0） */
    public boolean isRegCodeDisabled(SysUsers user) {
        RegCodeUser assignment = user == null ? null : getAssignment(user.getId());
        return assignment != null && assignment.getStatus() != null && assignment.getStatus() == STATUS_DISABLED;
    }

    /**
     * 底层子用户：parent_id 指向的不是 ROOT / 管理端账号（即在注册码页由客户创建的子用户）。
     * 父用户已不存在时也按底层处理（权限会被挡住）。
     */
    public boolean isBottomSubUser(SysUsers user) {
        if (!isSubAccount(user)) {
            return false;
        }
        SysUsers parent = sysUsersService.getById(user.getParentId().trim());
        return parent == null || !isAdminAccount(parent);
    }

    /** 能否在注册码页管理（查看 / 新建 / 停用 / 调整）自己的子用户：有注册码权限且是顶层账号 */
    public boolean canManageSubUsers(SysUsers user) {
        return canUseRegCode(user) && !isBottomSubUser(user);
    }

    /** 最多可创建的子用户数（reg_code_user.max_sub_users，没有记录为 0） */
    public int maxSubUsersOf(SysUsers user) {
        RegCodeUser assignment = user == null ? null : getAssignment(user.getId());
        return assignment == null || assignment.getMaxSubUsers() == null ? 0 : Math.max(assignment.getMaxSubUsers(), 0);
    }

    /** 已创建且处于启用状态的子用户数（停用的不计入上限） */
    public int enabledSubUserCount(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }
        List<SysUsers> children = sysUsersService.list(
                new LambdaQueryWrapper<SysUsers>().eq(SysUsers::getParentId, userId));
        if (children == null || children.isEmpty()) {
            return 0;
        }
        List<String> ids = children.stream().map(SysUsers::getId).filter(Objects::nonNull).toList();
        List<RegCodeUser> rows = regCodeUserService.list(
                new LambdaQueryWrapper<RegCodeUser>().in(RegCodeUser::getUserId, ids));
        Set<String> disabled = rows == null ? Set.of() : rows.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == STATUS_DISABLED)
                .map(RegCodeUser::getUserId)
                .collect(Collectors.toSet());
        return (int) ids.stream().filter(id -> !disabled.contains(id)).count();
    }

    /** 能否再新建子用户：能管理子用户，且启用中的子用户数 < max_sub_users */
    public boolean canCreateSubUsers(SysUsers user) {
        return canManageSubUsers(user) && enabledSubUserCount(user.getId()) < maxSubUsersOf(user);
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

    /** 管理端登录被拒时的提示：子账号 / 顶层注册码客户分别说明，其它情况给中性提示 */
    public String webBlockedMessage(SysUsers user) {
        if (isSubAccount(user)) {
            return "子用户请使用手机端登录，仅可生成注册码";
        }
        if (isRegCodeUser(user)) {
            return "注册码客户请使用手机端登录";
        }
        return "该账号请使用手机端登录";
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

    /**
     * 注册码客户 / 配置管理权限。无权限时抛 {@link ForbiddenException}（HTTP 403），
     * 只有取不到当前用户时才返回错误文案（正常情况下 token 拦截器已先挡住）。
     */
    public String requireManager(HttpServletRequest request) {
        SysUsers user = currentUser(request);
        if (user == null) {
            return "请先登录";
        }
        if (!isManager(user)) {
            throw new ForbiddenException("无权限管理注册码用户或配置");
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

    /** 某账号的按配置次数行 */
    public List<RegCodeUserConfig> quotaRows(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        List<RegCodeUserConfig> rows = regCodeUserConfigService.list(
                new LambdaQueryWrapper<RegCodeUserConfig>().eq(RegCodeUserConfig::getUserId, userId));
        return rows == null ? Collections.emptyList() : rows;
    }

    public List<String> listAssignedConfigIds(String userId) {
        return quotaRows(userId).stream()
                .map(RegCodeUserConfig::getConfigId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * @return null 表示 ROOT 可用全部；否则只有分配了次数行的配置（可能为空列表 = 暂无可用配置）
     */
    public List<String> allowedConfigIds(String userId) {
        return allowedConfigIds(userId == null || userId.isBlank() ? null : sysUsersService.getById(userId));
    }

    public List<String> allowedConfigIds(SysUsers user) {
        if (user == null) {
            return Collections.emptyList();
        }
        if (isRootUser(user)) {
            return null;
        }
        return listAssignedConfigIds(user.getId());
    }

    /** 生成前检查（真正扣次数在 RegCodeQuotaService 里用条件更新完成）；返回 null 表示可以生成 */
    public String assertCanGenerate(SysUsers user, String configId) {
        if (user == null) {
            return "请先登录";
        }
        if (isRootUser(user)) {
            return null;
        }
        List<RegCodeUserConfig> rows = quotaRows(user.getId());
        if (rows.isEmpty()) {
            return "暂无可用配置，请联系管理员分配";
        }
        if (configId == null || configId.isBlank()) {
            return "请选择注册码配置";
        }
        List<RegCodeUserConfig> matched = rows.stream().filter(r -> configId.equals(r.getConfigId())).toList();
        if (matched.isEmpty()) {
            return "无权使用该注册码配置";
        }
        int remaining = matched.stream().mapToInt(RegCodeAccessService::remainingOf).sum();
        if (remaining <= 0) {
            return "该配置的生成次数已用完";
        }
        return null;
    }

    public static int limitOf(RegCodeUserConfig row) {
        return row.getGenerateLimit() == null ? 0 : row.getGenerateLimit();
    }

    public static int usedOf(RegCodeUserConfig row) {
        return row.getGenerateUsed() == null ? 0 : row.getGenerateUsed();
    }

    public static int remainingOf(RegCodeUserConfig row) {
        return Math.max(limitOf(row) - usedOf(row), 0);
    }

    /** 按配置汇总成明细（同一配置有多行时合并），带配置名 */
    public List<RegCodeSubUser.QuotaItem> quotaItems(String userId) {
        java.util.Map<String, RegCodeSubUser.QuotaItem> map = new java.util.LinkedHashMap<>();
        for (RegCodeUserConfig row : quotaRows(userId)) {
            if (row.getConfigId() == null) {
                continue;
            }
            RegCodeSubUser.QuotaItem item = map.computeIfAbsent(row.getConfigId(), id -> {
                RegCodeSubUser.QuotaItem it = new RegCodeSubUser.QuotaItem();
                it.setConfigId(id);
                return it;
            });
            item.setAllocated(item.getAllocated() + limitOf(row));
            item.setUsed(item.getUsed() + usedOf(row));
        }
        if (!map.isEmpty()) {
            java.util.Map<String, String> names = new java.util.HashMap<>();
            List<springboot.domain.RegCodeConfig> configs = regCodeConfigService.listByIds(map.keySet());
            if (configs != null) {
                for (springboot.domain.RegCodeConfig c : configs) {
                    names.put(c.getId(), configLabel(c));
                }
            }
            map.values().forEach(it -> {
                it.setRemaining(Math.max(it.getAllocated() - it.getUsed(), 0));
                it.setConfigName(names.getOrDefault(it.getConfigId(), it.getConfigId()));
            });
        }
        return new java.util.ArrayList<>(map.values());
    }

    public static String configLabel(springboot.domain.RegCodeConfig c) {
        if (c == null) {
            return null;
        }
        String company = c.getCompany() == null ? "" : c.getCompany().trim();
        String name = c.getName() == null ? "" : c.getName().trim();
        if (company.isEmpty()) {
            return name;
        }
        return name.isEmpty() ? company : company + " / " + name;
    }

    public RegCodeQuotaVO quotaOf(SysUsers user) {
        RegCodeQuotaVO vo = new RegCodeQuotaVO();
        if (isRootUser(user)) {
            vo.setUnlimited(true);
            return vo;
        }
        vo.setUnlimited(false);
        List<RegCodeSubUser.QuotaItem> items = quotaItems(user == null ? null : user.getId());
        vo.setItems(items);
        int limit = items.stream().mapToInt(RegCodeSubUser.QuotaItem::getAllocated).sum();
        int used = items.stream().mapToInt(RegCodeSubUser.QuotaItem::getUsed).sum();
        vo.setGenerateLimit(limit);
        vo.setGenerateUsed(used);
        vo.setRemaining(items.stream().mapToInt(RegCodeSubUser.QuotaItem::getRemaining).sum());
        return vo;
    }
}
