package springboot.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import springboot.controller.web.SysRolesController;
import springboot.domain.SysMenus;
import springboot.domain.SysRoleMenu;
import springboot.service.SysMenusService;
import springboot.service.SysRoleMenuService;
import springboot.service.SysRolesService;
import springboot.service.impl.SysRoleMenuServiceImpl;
import springboot.utils.ApiResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色菜单保存（POST /admin/sysRoles/menus）只替换 client 指定的那一端；不传 client 直接拒绝、什么都不改。
 * 用一份内存里的 sys_role_menu 跑真实的 SysRoleMenuServiceImpl.assignMenus（remove / saveBatch 改成改内存）。
 */
class SysRoleMenusClientTest {

    /** roleId -> 已授权菜单 id（模拟 sys_role_menu） */
    Map<String, List<String>> table = new HashMap<>();
    Map<String, SysMenus> menus = new HashMap<>();
    SysRoleMenuServiceImpl service;
    SysRolesController controller;
    SysRolesService roles;

    private void menu(String id, String client, int disabled) {
        SysMenus m = new SysMenus();
        m.setId(id);
        m.setClient(client);
        m.setDisabled(disabled);
        menus.put(id, m);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        menu("menu_user", "admin", 0);
        menu("menu_crab", "admin", 0);
        menu("menu_old", "admin", 1);
        menu("menu_app_crab", "app", 0);
        menu("menu_app_regcode", "app", 0);
        table.put("role_ops", new ArrayList<>(List.of("menu_user", "menu_crab", "menu_old", "menu_app_crab", "menu_app_regcode")));

        SysMenusService sysMenus = mock(SysMenusService.class);
        when(sysMenus.listByIds(anyCollection())).thenAnswer(inv -> ((Collection<String>) inv.getArgument(0)).stream()
                .map(menus::get).filter(java.util.Objects::nonNull).collect(Collectors.toList()));
        service = spy(new SysRoleMenuServiceImpl());
        ReflectionTestUtils.setField(service, "sysMenusService", sysMenus);
        doAnswer(inv -> new ArrayList<>(table.getOrDefault((String) inv.getArgument(0), List.of())))
                .when(service).getMenuIdsByRole(anyString());
        // 这里只会按 roleId 删（assignMenus 里唯一的 remove），当前测试只有一个角色
        doAnswer(inv -> {
            table.put("role_ops", new ArrayList<>());
            return true;
        }).when(service).remove(any());
        doAnswer(inv -> {
            for (SysRoleMenu rm : (Collection<SysRoleMenu>) inv.getArgument(0)) {
                table.computeIfAbsent(rm.getRoldId(), k -> new ArrayList<>()).add(rm.getMenuId());
            }
            return true;
        }).when(service).saveBatch(anyCollection());

        roles = mock(SysRolesService.class);
        controller = new SysRolesController();
        ReflectionTestUtils.setField(controller, "sysRolesService", roles);
        ReflectionTestUtils.setField(controller, "sysRoleMenuService", service);
    }

    private ApiResponse save(Object client, String... ids) {
        Map<String, Object> body = new HashMap<>();
        body.put("roleId", "role_ops");
        body.put("menuIds", List.of(ids));
        if (client != null) {
            body.put("client", client);
        }
        return controller.assignRoleMenus(body);
    }

    private Set<String> grants() {
        return Set.copyOf(table.get("role_ops"));
    }

    @Test
    void savingAdminMenusKeepsAppGrants() {
        ApiResponse r = save("admin", "menu_user", "menu_app_regcode");
        assertEquals(200, r.getStatus());
        assertEquals(Set.of("menu_user", "menu_old", "menu_app_crab", "menu_app_regcode"), grants(),
                "取消了 menu_crab；app 端两条原样保留；停用菜单授权保留；提交里的 app id 被忽略");
    }

    @Test
    void savingAppMenusKeepsAdminGrants() {
        ApiResponse r = save("app", "menu_app_regcode", "menu_user");
        assertEquals(200, r.getStatus());
        assertEquals(Set.of("menu_user", "menu_crab", "menu_old", "menu_app_regcode"), grants(),
                "取消了 app 出货；admin 端授权原样保留；提交里的 admin id 被忽略");
        save("app");
        assertEquals(Set.of("menu_user", "menu_crab", "menu_old"), grants(), "app 端全部取消也不影响 admin 端");
    }

    @Test
    void missingOrInvalidClientIsRejectedAndNothingChanges() {
        Set<String> before = grants();
        for (Object client : new Object[]{null, "", "  ", "web"}) {
            ApiResponse r = save(client, "menu_user");
            assertEquals(500, r.getStatus(), "client=" + client);
            assertTrue(r.getMessage() != null && r.getMessage().contains("client"), r.getMessage());
            assertEquals(before, grants(), "client=" + client + " 时不能改任何授权");
        }
        verify(service, never()).remove(any());
        verify(service, never()).saveBatch(anyCollection());
        verify(roles, never()).getById(any());
        assertThrows(IllegalArgumentException.class, () -> service.assignMenus("role_ops", List.of("menu_user"), null),
                "service 层同样拒绝不带 client 的保存");
        assertEquals(before, grants());
    }
}
