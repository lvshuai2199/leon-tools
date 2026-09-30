package springboot.service.menu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import springboot.domain.SysMenus;
import springboot.domain.SysRoles;
import springboot.domain.SysUsers;
import springboot.service.SysMenusService;
import springboot.service.SysRoleMenuService;
import springboot.service.SysRolesService;
import springboot.utils.RoleUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按端（client）取当前用户可见的菜单：只取 disabled=0 且不是按钮的菜单；
 * ROOT 返回本端全部；其他角色返回授权菜单 + 其隐藏（visible=0）后代 + 祖先目录（已停用 / 其他端的祖先不补）。
 */
@Service
public class MenuQueryService {

    private final SysMenusService sysMenusService;
    private final SysRoleMenuService sysRoleMenuService;
    private final SysRolesService sysRolesService;

    public MenuQueryService(SysMenusService sysMenusService, SysRoleMenuService sysRoleMenuService,
                            SysRolesService sysRolesService) {
        this.sysMenusService = sysMenusService;
        this.sysRoleMenuService = sysRoleMenuService;
        this.sysRolesService = sysRolesService;
    }

    public List<SysMenus> menusFor(SysUsers user, String client) {
        if (user == null || user.getRoleId() == null || user.getRoleId().isBlank()) {
            return Collections.emptyList();
        }
        SysRoles role = sysRolesService.getById(user.getRoleId().trim());
        if (role == null && !RoleUtils.ROOT_ROLE_ID.equalsIgnoreCase(user.getRoleId().trim())) {
            return Collections.emptyList();
        }
        if (role != null && role.getIsDisabled() != null && role.getIsDisabled() != 0 && !RoleUtils.isRoot(role)) {
            return Collections.emptyList();
        }
        boolean root = role == null || RoleUtils.isRoot(role);
        List<String> granted = root ? List.of() : sysRoleMenuService.getMenuIdsByRole(role.getId());
        if (!root && (granted == null || granted.isEmpty())) {
            return Collections.emptyList();
        }
        return visibleMenus(enabledMenus(client), granted, root);
    }

    /** 本端启用的目录和菜单（不含按钮）；每行的 routeKey 都已换成完整路径（见 {@link #fillFullRouteKeys}） */
    public List<SysMenus> enabledMenus(String client) {
        LambdaQueryWrapper<SysMenus> w = new LambdaQueryWrapper<>();
        w.eq(SysMenus::getClient, client)
                .eq(SysMenus::getDisabled, 0)
                .ne(SysMenus::getMenuType, 2)
                .orderByAsc(SysMenus::getSortOrder);
        List<SysMenus> rows = sysMenusService.list(w);
        if (rows == null || rows.isEmpty()) {
            return rows == null ? Collections.emptyList() : rows;
        }
        List<SysMenus> lookup = List.of();
        if (rows.stream().anyMatch(MenuQueryService::needsParentPath)) {
            // 有还没回填 route_key 的行：父级可能已停用 / 是按钮，按本端全部行拼路径
            List<SysMenus> all = sysMenusService.list(new LambdaQueryWrapper<SysMenus>().eq(SysMenus::getClient, client));
            lookup = all == null ? List.of() : all;
        }
        fillFullRouteKeys(rows, lookup);
        return rows;
    }

    /**
     * 返回给前端（appMenus / 管理端菜单）的 routeKey 一律是完整路径（/crab/new、/crab/:id），永远不是相对段：
     * 已有的完整 route_key 只做规范化；还没回填的（手工新建、刚改过路径的菜单）或误存成相对段的，
     * 按 parent_id 链把 menu_url 拼成完整路径。拼不出（父级不存在、成环、没有 menu_url）时置空，不返回相对路径。
     *
     * @param rows   要填的行（原地修改）
     * @param lookup 用来找父级的其他行（可为空）
     */
    public static void fillFullRouteKeys(List<SysMenus> rows, Collection<SysMenus> lookup) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<String, SysMenus> byId = new HashMap<>();
        for (SysMenus m : rows) {
            if (m.getId() != null) {
                byId.put(m.getId(), m);
            }
        }
        if (lookup != null) {
            for (SysMenus m : lookup) {
                if (m.getId() != null) {
                    byId.putIfAbsent(m.getId(), m);
                }
            }
        }
        Map<String, String> memo = new HashMap<>();
        Map<SysMenus, String> result = new java.util.IdentityHashMap<>();
        for (SysMenus m : rows) {
            result.put(m, fullPath(m, byId, memo, 0));
        }
        result.forEach(SysMenus::setRouteKey);
    }

    /** route_key 为空，或不是以 / 开头的相对段（且有父级）：需要按父级拼路径 */
    private static boolean needsParentPath(SysMenus m) {
        String key = m.getRouteKey() == null ? "" : m.getRouteKey().trim();
        return key.isEmpty() || (!key.startsWith("/") && !MenuPaths.isExternal(key) && !isTop(m.getParentId()));
    }

    private static String fullPath(SysMenus m, Map<String, SysMenus> byId, Map<String, String> memo, int depth) {
        if (depth > 20) {
            return null;
        }
        if (m.getId() != null && memo.containsKey(m.getId())) {
            return memo.get(m.getId());
        }
        String key = m.getRouteKey() == null ? "" : m.getRouteKey().trim();
        String url = key.isEmpty() ? (m.getMenuUrl() == null ? "" : m.getMenuUrl().trim()) : key;
        String result;
        if (url.isEmpty()) {
            result = null;
        } else if (MenuPaths.isExternal(url) || url.startsWith("/") || isTop(m.getParentId())) {
            result = MenuPaths.normalize(url);
        } else {
            SysMenus parent = byId.get(m.getParentId().trim());
            String parentPath = parent == null || parent == m ? null : fullPath(parent, byId, memo, depth + 1);
            result = parentPath == null || MenuPaths.isExternal(parentPath) ? null : MenuPaths.join(parentPath, url);
        }
        if (m.getId() != null) {
            memo.put(m.getId(), result);
        }
        return result;
    }

    private static boolean isTop(String parentId) {
        return parentId == null || parentId.isBlank() || "0".equals(parentId.trim());
    }

    /**
     * @param available 本端启用的菜单（已过滤停用和按钮）
     * @param granted   角色授权的菜单 id（可能含其他端 / 已停用 / 不存在的 id，会被忽略）
     */
    public static List<SysMenus> visibleMenus(List<SysMenus> available, Collection<String> granted, boolean root) {
        if (available == null || available.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysMenus> sorted = new ArrayList<>(available);
        sorted.sort(Comparator.comparing((SysMenus m) -> m.getSortOrder() == null ? Integer.MAX_VALUE : m.getSortOrder())
                .thenComparing(m -> m.getId() == null ? "" : m.getId()));
        if (root) {
            return sorted;
        }
        Map<String, SysMenus> byId = new HashMap<>();
        Map<String, List<SysMenus>> children = new HashMap<>();
        for (SysMenus m : sorted) {
            byId.put(m.getId(), m);
            if (m.getParentId() != null) {
                children.computeIfAbsent(m.getParentId(), k -> new ArrayList<>()).add(m);
            }
        }
        Set<String> visible = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        if (granted != null) {
            for (String id : granted) {
                if (id != null && byId.containsKey(id) && visible.add(id)) {
                    queue.add(id);
                }
            }
        }
        // 授权页面的隐藏后代（如 /crab/new、/crab/:id）自动可见
        while (!queue.isEmpty()) {
            for (SysMenus c : children.getOrDefault(queue.poll(), List.of())) {
                if (Integer.valueOf(0).equals(c.getVisible()) && visible.add(c.getId())) {
                    queue.add(c.getId());
                }
            }
        }
        // 补祖先目录（只补本端启用的）
        for (String id : new ArrayList<>(visible)) {
            SysMenus cur = byId.get(id);
            int guard = 0;
            while (cur != null && cur.getParentId() != null && !"0".equals(cur.getParentId()) && guard++ < 50) {
                SysMenus parent = byId.get(cur.getParentId());
                if (parent == null) {
                    break;
                }
                visible.add(parent.getId());
                cur = parent;
            }
        }
        return sorted.stream().filter(m -> visible.contains(m.getId())).toList();
    }
}
