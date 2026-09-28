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

    /** 本端启用的目录和菜单（不含按钮） */
    public List<SysMenus> enabledMenus(String client) {
        LambdaQueryWrapper<SysMenus> w = new LambdaQueryWrapper<>();
        w.eq(SysMenus::getClient, client)
                .eq(SysMenus::getDisabled, 0)
                .ne(SysMenus::getMenuType, 2)
                .orderByAsc(SysMenus::getSortOrder);
        return sysMenusService.list(w);
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
