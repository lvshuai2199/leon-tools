package springboot.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import springboot.DTO.UserDto;
import springboot.DTO.UsersDelDto;
import springboot.controller.web.SysUserController;
import springboot.domain.SysRoles;
import springboot.domain.SysUsers;
import springboot.service.RegCodeAccessService;
import springboot.service.SysRoleMenuService;
import springboot.service.SysRolesService;
import springboot.service.SysUsersService;
import springboot.utils.ForbiddenException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SysUserControllerPrivilegeTest {

    SysUsersService users;
    SysRolesService roles;
    SysRoleMenuService roleMenus;
    RegCodeAccessService access;
    SysUserController controller;
    SysUsers operator;
    MockHttpServletRequest req = new MockHttpServletRequest();

    static SysUsers u(String id, String roleId) {
        SysUsers x = new SysUsers();
        x.setId(id);
        x.setUsername(id);
        x.setRoleId(roleId);
        return x;
    }

    static SysRoles role(String id, String name) {
        SysRoles r = new SysRoles();
        r.setId(id);
        r.setRoleName(name);
        return r;
    }

    static UserDto dto(String id, String roleId) {
        UserDto d = new UserDto();
        d.setId(id);
        d.setUsername("name_" + (id == null ? "new" : id));
        d.setRoleId(roleId);
        d.setPassword("123456");
        return d;
    }

    @BeforeEach
    void setUp() {
        users = mock(SysUsersService.class);
        roles = mock(SysRolesService.class);
        roleMenus = mock(SysRoleMenuService.class);
        access = mock(RegCodeAccessService.class);
        controller = new SysUserController();
        ReflectionTestUtils.setField(controller, "sysUsersService", users);
        ReflectionTestUtils.setField(controller, "sysRolesService", roles);
        ReflectionTestUtils.setField(controller, "sysRoleMenuService", roleMenus);
        ReflectionTestUtils.setField(controller, "regCodeAccessService", access);

        operator = u("mgr", "role_mgr");
        SysUsers root = u("root", "role_root");
        when(access.currentUser(any())).thenAnswer(inv -> operator);
        when(access.isRootUser(any(SysUsers.class))).thenAnswer(inv -> {
            SysUsers x = inv.getArgument(0);
            return x != null && "role_root".equals(x.getRoleId());
        });
        when(access.menuIdsOf(any())).thenReturn(List.of("menu_user", "menu_role", "menu_tasks"));
        when(users.getById("root")).thenReturn(root);
        when(users.getById("mgr")).thenReturn(operator);
        when(users.getById("u1")).thenReturn(u("u1", "role_small"));
        when(users.updateById(any())).thenReturn(true);
        when(users.save(any())).thenReturn(true);
        when(roles.getById("role_root")).thenReturn(role("role_root", "ROOT"));
        when(roles.getById("role_small")).thenReturn(role("role_small", "小角色"));
        when(roles.getById("role_big")).thenReturn(role("role_big", "大角色"));
        when(roleMenus.getMenuIdsByRole("role_small")).thenReturn(List.of("menu_tasks"));
        when(roleMenus.getMenuIdsByRole("role_big")).thenReturn(List.of("menu_tasks", "menu_menu"));
    }

    @Test
    void nonRootCannotTouchRootOrEscalate() {
        assertThrows(ForbiddenException.class, () -> controller.sysUserRegister(dto("root", null), req), "不能改 ROOT 用户");
        assertThrows(ForbiddenException.class, () -> controller.sysUserRegister(dto("u1", "role_root"), req), "不能授予 ROOT");
        assertThrows(ForbiddenException.class, () -> controller.sysUserRegister(dto(null, "role_root"), req));
        assertThrows(ForbiddenException.class, () -> controller.sysUserRegister(dto("mgr", "role_small"), req), "不能改自己的角色");
        assertThrows(ForbiddenException.class, () -> controller.sysUserRegister(dto("u1", "role_big"), req), "角色菜单超出自己的");
        assertDoesNotThrow(() -> controller.sysUserRegister(dto("u1", "role_small"), req));
        assertDoesNotThrow(() -> controller.sysUserRegister(dto("mgr", "role_mgr"), req), "角色不变可以改自己资料");
        assertDoesNotThrow(() -> controller.sysUserRegister(dto("u1", null), req));
    }

    @Test
    void nonRootCannotDeleteRootOrSelf() {
        UsersDelDto d = new UsersDelDto();
        d.setUserIds(List.of("u1", "root"));
        when(users.listByIds(any())).thenReturn(List.of(u("u1", "role_small"), u("root", "role_root")));
        assertThrows(ForbiddenException.class, () -> controller.delUsers(d, req));
        d.setUserIds(List.of("mgr"));
        assertThrows(ForbiddenException.class, () -> controller.delUsers(d, req));
    }

    @Test
    void rootIsUnrestricted() {
        operator = u("root", "role_root");
        assertDoesNotThrow(() -> controller.sysUserRegister(dto("u1", "role_root"), req));
        assertEquals(200, controller.sysUserRegister(dto("u1", "role_big"), req).getStatus());
    }
}
