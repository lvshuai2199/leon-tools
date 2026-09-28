package springboot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import springboot.domain.SysUsers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegCodeAccessServiceTest {

    SysUsersService users;
    RegCodeAccessService svc;

    @BeforeEach
    void setUp() {
        users = mock(SysUsersService.class);
        svc = new RegCodeAccessService(users, mock(SysRolesService.class), mock(SysRoleMenuService.class),
                mock(SysMenusService.class), mock(RegCodeUserService.class), mock(RegCodeUserConfigService.class));
    }

    static SysUsers u(String id, String roleId, String parentId) {
        SysUsers x = new SysUsers();
        x.setId(id);
        x.setUsername(id);
        x.setRoleId(roleId);
        x.setParentId(parentId);
        return x;
    }

    @Test
    void regCodeRule() {
        SysUsers root = u("root", "role_root", null);
        SysUsers client = u("client", "role_regcode_client", "mgr");
        SysUsers mgr = u("mgr", "role_admin", null);
        when(users.getById("root")).thenReturn(root);
        when(users.getById("client")).thenReturn(client);
        when(users.getById("mgr")).thenReturn(mgr);

        assertTrue(svc.canUseRegCode(root));
        assertTrue(svc.canUseRegCode(client));
        assertFalse(svc.canUseRegCode(mgr), "普通管理员（非 ROOT、非注册码用户）不能生成");
        assertFalse(svc.canUseRegCode(null));
        assertTrue(svc.canUseRegCode(u("s1", "role_x", "root")), "父用户是 ROOT 的子账号可以");
        assertTrue(svc.canUseRegCode(u("s2", "role_x", "client")), "父用户是注册码用户的子账号可以");
        assertFalse(svc.canUseRegCode(u("s3", "role_x", "mgr")), "父用户是普通管理员的子账号不行");
        assertFalse(svc.canUseRegCode(u("s4", "role_x", "gone")), "父用户不存在不行");
    }

    @Test
    void parentIsLookedUpEveryTime() {
        when(users.getById("root")).thenReturn(u("root", "role_root", null));
        SysUsers sub = u("s1", "role_x", "root");
        svc.canUseRegCode(sub);
        svc.canUseRegCode(sub);
        verify(users, times(2)).getById("root");
    }

    @Test
    void regCodeUserIsRoleOnlyAndCrabRule() {
        SysUsers plainSub = u("s", "role_x", "p");
        assertFalse(svc.isRegCodeUser(plainSub), "挂了父用户不等于注册码用户");
        assertTrue(svc.isSubAccount(plainSub));
        assertTrue(svc.canUseCrab(plainSub), "普通子账号可以用出货");
        assertFalse(svc.canUseCrab(u("c", "role_regcode_client", "p")), "注册码客户不能用出货");
        assertTrue(svc.canUseCrab(u("m", "role_admin", null)));
        assertFalse(svc.canLoginWeb(plainSub));
        assertFalse(svc.canLoginWeb(u("c2", "role_regcode_client", null)));
        assertTrue(svc.canLoginWeb(u("m", "role_admin", null)));
    }
}
