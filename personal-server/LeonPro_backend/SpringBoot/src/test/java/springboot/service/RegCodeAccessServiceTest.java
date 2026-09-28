package springboot.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import springboot.domain.RegCodeUser;
import springboot.domain.RegCodeUserConfig;
import springboot.domain.SysRoles;
import springboot.domain.SysUsers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegCodeAccessServiceTest {

    SysUsersService users;
    SysRolesService roles;
    SysRoleMenuService roleMenus;
    RegCodeUserService regCodeUsers;
    RegCodeAccessService svc;
    /** userId -> reg_code_user 状态 */
    Map<String, Integer> status = new HashMap<>();
    /** roleId -> 菜单 */
    Map<String, List<String>> menus = new HashMap<>();

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUsers.class);
        TableInfoHelper.initTableInfo(assistant, RegCodeUser.class);
        TableInfoHelper.initTableInfo(assistant, RegCodeUserConfig.class);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        users = mock(SysUsersService.class);
        roles = mock(SysRolesService.class);
        roleMenus = mock(SysRoleMenuService.class);
        regCodeUsers = mock(RegCodeUserService.class);
        svc = new RegCodeAccessService(users, roles, roleMenus, mock(SysMenusService.class), regCodeUsers,
                mock(RegCodeUserConfigService.class), mock(RegCodeConfigService.class));
        when(roleMenus.getMenuIdsByRole(anyString())).thenAnswer(inv -> menus.getOrDefault((String) inv.getArgument(0), List.of()));
        when(regCodeUsers.getOne(any(Wrapper.class), anyBoolean())).thenAnswer(inv -> {
            AbstractWrapper<?, ?, ?> w = inv.getArgument(0);
            w.getSqlSegment();
            for (Object v : w.getParamNameValuePairs().values()) {
                if (status.containsKey(String.valueOf(v))) {
                    RegCodeUser a = new RegCodeUser();
                    a.setUserId(String.valueOf(v));
                    a.setStatus(status.get(String.valueOf(v)));
                    a.setMaxSubUsers(3);
                    return a;
                }
            }
            return null;
        });
        menus.put("role_regcode_client", List.of("menu_regcode_center", "menu_regcode"));
        menus.put("role_cust", List.of("menu_regcode"));
        menus.put("role_gen_admin", List.of("menu_regcode", "menu_user"));
        menus.put("role_admin", List.of("menu_user", "menu_regcode_user"));
    }

    SysUsers u(String id, String roleId, String parentId) {
        SysUsers x = new SysUsers();
        x.setId(id);
        x.setUsername(id);
        x.setRoleId(roleId);
        x.setParentId(parentId);
        when(users.getById(id)).thenReturn(x);
        return x;
    }

    @Test
    void accessByRolePermission() {
        assertTrue(svc.canUseRegCode(u("root", "role_root", null)), "ROOT 可以");
        assertTrue(svc.canUseRegCode(u("genAdmin", "role_gen_admin", null)), "角色有注册码生成菜单的管理员可以");
        assertFalse(svc.canUseRegCode(u("admin", "role_admin", null)), "只有客户管理菜单、没有生成菜单不行");
        assertTrue(svc.canUseRegCode(u("cust", "role_regcode_client", "admin")), "后台建的客户（父用户是管理员）按自己角色判断");
        assertFalse(svc.canUseRegCode(null));
    }

    @Test
    void bottomSubUserFollowsCreatorEveryRequest() {
        u("admin", "role_admin", null);
        SysUsers cust = u("cust", "role_cust", "admin");
        SysUsers sub = u("sub", "role_regcode_client", "cust");
        status.put("cust", 1);
        status.put("sub", 1);
        assertTrue(svc.isBottomSubUser(sub));
        assertFalse(svc.isBottomSubUser(cust), "父用户是管理员的客户是顶层");
        assertTrue(svc.canUseRegCode(sub));

        menus.put("role_cust", List.of("menu_user"));
        assertFalse(svc.canUseRegCode(sub), "创建人角色失去注册码权限，下一次请求就不行");
        menus.put("role_cust", List.of("menu_regcode"));
        assertTrue(svc.canUseRegCode(sub));

        status.put("cust", 0);
        assertFalse(svc.canUseRegCode(sub), "创建人被停用");
        status.put("cust", 1);

        SysRoles off = new SysRoles();
        off.setId("role_cust");
        off.setIsDisabled(1);
        when(roles.getById("role_cust")).thenReturn(off);
        assertFalse(svc.canUseRegCode(sub), "创建人角色被禁用");
        when(roles.getById("role_cust")).thenReturn(null);

        status.put("sub", 0);
        assertFalse(svc.canUseRegCode(sub), "子用户自己被停用");
        status.put("sub", 1);

        when(users.getById("cust")).thenReturn(null);
        assertFalse(svc.canUseRegCode(sub), "创建人被删除");
        verify(users, atLeast(5)).getById("cust");
    }

    @Test
    void onlyOneLayer() {
        u("admin", "role_admin", null);
        SysUsers cust = u("cust", "role_cust", "admin");
        SysUsers sub = u("sub", "role_regcode_client", "cust");
        SysUsers subSub = u("subsub", "role_regcode_client", "sub");
        assertTrue(svc.canManageSubUsers(cust), "顶层客户可以管理子用户");
        assertFalse(svc.canManageSubUsers(sub), "底层子用户不能再建");
        assertFalse(svc.canUseRegCode(subSub), "第三层（历史数据）不能用");
        SysUsers custNoParent = u("c0", "role_regcode_client", null);
        assertTrue(svc.canManageSubUsers(custNoParent), "parent_id 为空的注册码账号也是顶层");
        assertEquals(0, svc.maxSubUsersOf(u("nobody", "role_cust", null)), "没有注册码账号记录 = 0");
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
