package springboot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import springboot.domain.SysMenus;
import springboot.domain.SysRoleMenu;
import springboot.mapper.SysRoleMenuMapper;
import springboot.service.SysMenusService;
import springboot.service.SysRoleMenuService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
* @author 13326
* @description 针对表【sys_role_menu】的数据库操作Service实现
* @createDate 2025-04-15 17:13:27
*/
@Service
public class SysRoleMenuServiceImpl extends ServiceImpl<SysRoleMenuMapper, SysRoleMenu>
    implements SysRoleMenuService {

    @Autowired
    private SysMenusService sysMenusService;

    @Override
    public List<String> getMenuIdsByRole(String roleId) {
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenu::getRoldId, roleId);
        return this.list(wrapper).stream()
            .map(SysRoleMenu::getMenuId)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(String roleId, List<String> menuIds, String client) {
        if (client == null || !springboot.service.menu.MenuClients.isValid(client.trim())) {
            throw new IllegalArgumentException("保存角色菜单必须指定端 client（admin / app）");
        }
        client = client.trim();
        List<String> existing = getMenuIdsByRole(roleId);
        Set<String> ids = new HashSet<>(existing);
        if (menuIds != null) {
            ids.addAll(menuIds);
        }
        Map<String, SysMenus> menus = ids.isEmpty() ? Map.of()
                : sysMenusService.listByIds(ids).stream()
                    .collect(Collectors.toMap(SysMenus::getId, Function.identity(), (a, b) -> a));
        List<String> finalIds = mergeGrants(existing, menuIds, menus, client);
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenu::getRoldId, roleId);
        this.remove(wrapper);
        if (!finalIds.isEmpty()) {
            List<SysRoleMenu> relations = finalIds.stream()
                .map(menuId -> {
                    SysRoleMenu rm = new SysRoleMenu();
                    rm.setRoldId(roleId);
                    rm.setMenuId(menuId);
                    return rm;
                })
                .collect(Collectors.toList());
            this.saveBatch(relations);
        }
    }

    /**
     * 角色授权保存后的最终菜单 id：
     * <ul>
     *   <li>原有授权里指向已停用菜单（disabled=1）的保留——停用菜单在角色页不显示，保存时不能把它的授权删掉，重新启用后授权自动恢复；</li>
     *   <li>只替换 client 这一端的授权，其他端的原有授权保留，提交里其他端的 id 忽略
     *       （client 为 null 时整体替换——只保留给老调用方的静态方法，接口和 assignMenus 都要求 client）；</li>
     *   <li>提交里不存在的菜单 id 忽略（不再产生指向不存在菜单的授权行）。</li>
     * </ul>
     */
    public static List<String> mergeGrants(Collection<String> existing, Collection<String> requested,
                                           Map<String, SysMenus> menus, String client) {
        Set<String> result = new LinkedHashSet<>();
        if (existing != null) {
            for (String id : existing) {
                SysMenus m = id == null ? null : menus.get(id);
                if (m == null) {
                    continue;
                }
                boolean disabled = m.getDisabled() != null && m.getDisabled() != 0;
                boolean otherClient = client != null && !client.equals(clientOf(m));
                if (disabled || otherClient) {
                    result.add(id);
                }
            }
        }
        if (requested != null) {
            for (String id : requested) {
                SysMenus m = id == null ? null : menus.get(id);
                if (m == null) {
                    continue;
                }
                if (client != null && !client.equals(clientOf(m))) {
                    continue;
                }
                result.add(id);
            }
        }
        return new ArrayList<>(result);
    }

    private static String clientOf(SysMenus m) {
        return m.getClient() == null || m.getClient().isBlank() ? "admin" : m.getClient();
    }
}
