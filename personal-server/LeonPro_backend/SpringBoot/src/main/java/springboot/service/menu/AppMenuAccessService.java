package springboot.service.menu;

import org.springframework.stereotype.Service;
import springboot.domain.SysMenus;
import springboot.domain.SysUsers;
import springboot.service.RegCodeAccessService;
import springboot.service.SysRoleMenuService;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GET /auth/me 的 appMenus：用户端能显示哪些菜单。前端只按 appMenus 显示入口，所以这里必须和后端接口的权限判断完全一致：
 * <ul>
 *   <li>出货菜单（route_key = app-crab-route，默认 /crab）及其子页：当且仅当
 *       {@link RegCodeAccessService#canUseCrab}（/app/crabShipment/** 用的同一个判断）为 true 才返回；</li>
 *   <li>羽毛球计费菜单（route_key = app-badminton-route，默认 /badminton）及其子页：当且仅当
 *       {@link RegCodeAccessService#canUseBadminton}（/app/badmintonBill/** 用的同一个判断）为 true 才返回；</li>
 *   <li>注册码菜单（route_key = app-regcode-route，默认 /regcode）及其子页：当且仅当
 *       {@link RegCodeAccessService#canUseRegCode}（/common/** 用的同一个判断）为 true 才返回；</li>
 *   <li>其他用户端菜单：按 {@link RegCodeAccessService#appMenuGoverningUser} 的角色授权（子账号看创建人）；</li>
 *   <li>注册码子用户（{@link RegCodeAccessService#isBottomSubUser}）：只能生成注册码，appMenus 只有 /regcode 这一项
 *       （创建人仍有注册码权限时），不管它自己的角色勾了什么；</li>
 *   <li>ROOT：本端全部启用的菜单。授权菜单的隐藏子页和祖先目录一并返回（见 {@link MenuQueryService#visibleMenus}）。</li>
 * </ul>
 * 只返回库里存在且未停用的菜单。清单还没同步、库里没有用户端出货 / 注册码菜单时，接口权限会退回看管理端
 * menu_crab / menu_regcode，但 appMenus 里没有对应入口（没有菜单可返回）。
 */
@Service
public class AppMenuAccessService {

    private final MenuQueryService menuQueryService;
    private final RegCodeAccessService access;
    private final SysRoleMenuService sysRoleMenuService;

    public AppMenuAccessService(MenuQueryService menuQueryService, RegCodeAccessService access,
                                SysRoleMenuService sysRoleMenuService) {
        this.menuQueryService = menuQueryService;
        this.access = access;
        this.sysRoleMenuService = sysRoleMenuService;
    }

    public List<SysMenus> appMenusFor(SysUsers user) {
        if (user == null) {
            return Collections.emptyList();
        }
        List<SysMenus> available = menuQueryService.enabledMenus(MenuClients.APP);
        if (available == null || available.isEmpty()) {
            return Collections.emptyList();
        }
        if (access.isRootUser(user)) {
            return dropOrphans(MenuQueryService.visibleMenus(available, List.of(), true));
        }
        String crabRoot = idOfRoute(available, access.appCrabRoute());
        String badmintonRoot = idOfRoute(available, access.appBadmintonRoute());
        String regRoot = idOfRoute(available, access.appRegCodeRoute());
        Set<String> crabTree = subtree(available, crabRoot);
        Set<String> badmintonTree = subtree(available, badmintonRoot);
        Set<String> regTree = subtree(available, regRoot);
        boolean crab = access.canUseCrab(user);
        boolean badminton = access.canUseBadminton(user);
        boolean reg = access.canUseRegCode(user);
        if (access.isBottomSubUser(user)) {
            // 注册码子用户：独立账号，只能生成注册码；不带 /regcode 的子页，也不看角色的其他授权
            return reg && regRoot != null
                    ? available.stream().filter(m -> regRoot.equals(m.getId())).toList()
                    : Collections.emptyList();
        }

        Set<String> granted = new LinkedHashSet<>();
        if (crab && crabRoot != null) {
            granted.add(crabRoot);
        }
        if (badminton && badmintonRoot != null) {
            granted.add(badmintonRoot);
        }
        if (reg && regRoot != null) {
            granted.add(regRoot);
        }
        SysUsers governing = access.appMenuGoverningUser(user);
        if (governing != null) {
            List<String> roleGrants;
            if (access.isRootUser(governing)) {
                roleGrants = available.stream().map(SysMenus::getId).toList();
            } else {
                String roleId = governing.getRoleId();
                roleGrants = roleId == null || roleId.isBlank() ? List.of() : sysRoleMenuService.getMenuIdsByRole(roleId.trim());
            }
            if (roleGrants != null) {
                for (String id : roleGrants) {
                    if (id == null) {
                        continue;
                    }
                    if (crabTree.contains(id) ? crab
                            : badmintonTree.contains(id) ? badminton
                            : regTree.contains(id) ? reg : true) {
                        granted.add(id);
                    }
                }
            }
        }
        if (granted.isEmpty()) {
            return Collections.emptyList();
        }
        // 最后再按权限过滤一次：其他菜单的隐藏子页 / 祖先补全不能把出货、注册码的页面带进来
        return dropOrphans(MenuQueryService.visibleMenus(available, granted, false).stream()
                .filter(m -> crabTree.contains(m.getId()) ? crab
                        : badmintonTree.contains(m.getId()) ? badminton
                        : !regTree.contains(m.getId()) || reg)
                .toList());
    }

    /**
     * 去掉上级不在结果里的菜单（上级已停用 / 没权限）：例如出货菜单停用后，它下面的 /crab/new、/crab/:id 也不能单独出现，
     * 否则前端会显示一个接口必然 403 的页面。
     */
    static List<SysMenus> dropOrphans(List<SysMenus> menus) {
        List<SysMenus> current = menus;
        while (true) {
            Set<String> ids = new HashSet<>();
            current.forEach(m -> ids.add(m.getId()));
            List<SysMenus> next = current.stream()
                    .filter(m -> m.getParentId() == null || m.getParentId().isBlank() || "0".equals(m.getParentId())
                            || ids.contains(m.getParentId()))
                    .toList();
            if (next.size() == current.size()) {
                return next;
            }
            current = next;
        }
    }

    private static String idOfRoute(List<SysMenus> available, String route) {
        if (route == null || route.isBlank()) {
            return null;
        }
        String key = route.trim();
        for (SysMenus m : available) {
            if (key.equals(m.getRouteKey())) {
                return m.getId();
            }
        }
        return null;
    }

    /** 某菜单及其全部后代（本端启用的）的 id */
    private static Set<String> subtree(List<SysMenus> available, String rootId) {
        if (rootId == null) {
            return Set.of();
        }
        Map<String, List<String>> children = new HashMap<>();
        for (SysMenus m : available) {
            if (m.getParentId() != null) {
                children.computeIfAbsent(m.getParentId(), k -> new java.util.ArrayList<>()).add(m.getId());
            }
        }
        Set<String> out = new HashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (id != null && out.add(id)) {
                queue.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return out;
    }
}
