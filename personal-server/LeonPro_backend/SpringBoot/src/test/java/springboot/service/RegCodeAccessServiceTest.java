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
import springboot.domain.SysMenus;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegCodeAccessServiceTest {

    SysUsersService users;
    SysRolesService roles;
    SysRoleMenuService roleMenus;
    RegCodeUserService regCodeUsers;
    SysMenusService sysMenus;
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
        TableInfoHelper.initTableInfo(assistant, SysMenus.class);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        users = mock(SysUsersService.class);
        roles = mock(SysRolesService.class);
        roleMenus = mock(SysRoleMenuService.class);
        regCodeUsers = mock(RegCodeUserService.class);
        sysMenus = mock(SysMenusService.class);
        svc = new RegCodeAccessService(users, roles, roleMenus, sysMenus, regCodeUsers,
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
        u("p", "role_admin", null);
        assertFalse(svc.canUseCrab(plainSub), "创建人角色没有出货菜单，子账号也不能用");
        menus.put("role_admin", List.of("menu_user", "menu_regcode_user", "menu_crab"));
        assertTrue(svc.canUseCrab(plainSub), "创建人角色有出货菜单，子账号跟着可以用");
        assertFalse(svc.canUseCrab(u("c", "role_regcode_client", "p")), "注册码客户不能用出货");
        assertTrue(svc.canUseCrab(u("m", "role_admin", null)));
        assertFalse(svc.canLoginWeb(plainSub));
        assertFalse(svc.canLoginWeb(u("c2", "role_regcode_client", null)));
        assertTrue(svc.canLoginWeb(u("m", "role_admin", null)));
    }

    /** route_key -> 用户端菜单（按查询条件里的路由返回，出货和注册码分开） */
    Map<String, SysMenus> appMenusByRoute = new HashMap<>();

    @SuppressWarnings("unchecked")
    private void appMenu(String route, String id, int disabled) {
        SysMenus m = new SysMenus();
        m.setId(id);
        m.setClient("app");
        m.setRouteKey(route);
        m.setDisabled(disabled);
        appMenusByRoute.put(route, m);
        when(sysMenus.list(any(Wrapper.class))).thenAnswer(inv -> {
            AbstractWrapper<?, ?, ?> w = inv.getArgument(0);
            w.getSqlSegment();
            for (Object v : w.getParamNameValuePairs().values()) {
                SysMenus hit = appMenusByRoute.get(String.valueOf(v));
                if (hit != null) {
                    return List.of(hit);
                }
            }
            return List.of();
        });
    }

    private void appCrabMenu(String id, int disabled) {
        appMenu("/crab", id, disabled);
    }

    @Test
    void crabNeedsMenuFallbackToAdminMenuCrab() {
        menus.put("role_ops", List.of("menu_crab"));
        menus.put("role_x", List.of("menu_user"));
        assertEquals("menu_crab", svc.effectiveCrabMenuId(), "还没有用户端出货菜单时看 menu_crab");
        assertTrue(svc.canUseCrab(u("root", "role_root", null)), "ROOT 可以");
        assertTrue(svc.canUseCrab(u("ops", "role_ops", null)), "角色有 menu_crab");
        assertFalse(svc.canUseCrab(u("x", "role_x", null)), "角色没有出货菜单");
        assertFalse(svc.canUseCrab(u("norole", null, null)), "没有角色");
        assertFalse(svc.canUseCrab(null));
    }

    @Test
    void crabSubAccountFollowsCreatorEveryRequest() {
        menus.put("role_ops", List.of("menu_crab"));
        menus.put("role_x", List.of("menu_user"));
        SysUsers ops = u("ops", "role_ops", null);
        SysUsers sub = u("sub", "role_x", "ops");
        assertTrue(svc.canUseCrab(sub), "子账号自己角色没有也行，看创建人");
        assertFalse(svc.canUseCrab(u("subX", "role_ops", "xboss")), "子账号自己角色有但创建人不存在");
        u("xboss", "role_x", null);
        assertFalse(svc.canUseCrab(u("subY", "role_ops", "xboss")), "子账号自己角色有但创建人没有：不行");

        menus.put("role_ops", List.of("menu_user"));
        assertFalse(svc.canUseCrab(sub), "创建人角色失去出货菜单，下一次请求就不行");
        menus.put("role_ops", List.of("menu_crab"));

        SysRoles off = new SysRoles();
        off.setId("role_ops");
        off.setIsDisabled(1);
        when(roles.getById("role_ops")).thenReturn(off);
        assertFalse(svc.canUseCrab(sub), "创建人角色被禁用");
        when(roles.getById("role_ops")).thenReturn(null);

        status.put("ops", 0);
        assertFalse(svc.canUseCrab(sub), "创建人账号被停用");
        status.put("ops", 1);
        status.put("sub", 0);
        assertFalse(svc.canUseCrab(sub), "子账号自己被停用");
        status.remove("sub");
        assertTrue(svc.canUseCrab(sub));

        assertFalse(svc.canUseCrab(u("subsub", "role_x", "sub")), "只允许一层：创建人自己是子账号");
        assertFalse(svc.canUseCrab(u("subRc", "role_regcode_client", "ops")), "子账号是注册码客户角色");
        menus.put("role_regcode_client", List.of("menu_regcode", "menu_crab"));
        u("rc", "role_regcode_client", null);
        assertFalse(svc.canUseCrab(u("subOfRc", "role_x", "rc")), "创建人是注册码客户：永远不行");
        assertFalse(svc.canUseCrab(u("rc2", "role_regcode_client", null)), "注册码客户角色即使误勾了出货菜单也不行");
        u("root", "role_root", null);
        assertTrue(svc.canUseCrab(u("subOfRoot", "role_x", "root")), "ROOT 建的子账号可以");
        assertTrue(ops.getParentId() == null);
    }

    @Test
    void crabUsesAppMenuAfterSync() {
        menus.put("role_ops", List.of("menu_crab"));
        menus.put("role_boss", List.of("menu_app_crab"));
        appCrabMenu("menu_app_crab", 0);
        assertEquals("menu_app_crab", svc.effectiveCrabMenuId());
        assertFalse(svc.canUseCrab(u("ops", "role_ops", null)), "同步后只认用户端出货菜单，只有 menu_crab 不行");
        assertTrue(svc.canUseCrab(u("boss", "role_boss", null)));
        assertTrue(svc.canUseCrab(u("bossSub", "role_x", "boss")), "子账号跟着创建人");

        appCrabMenu("menu_app_crab", 1);
        assertEquals(null, svc.effectiveCrabMenuId(), "用户端出货菜单已停用");
        assertFalse(svc.canUseCrab(u("boss", "role_boss", null)), "菜单停用后谁都不能用");
        assertTrue(svc.canUseCrab(u("root", "role_root", null)), "ROOT 除外");
    }

    @Test
    void regCodeUsesAppMenuAfterSync() {
        menus.put("role_app_gen", List.of("menu_app_regcode"));
        assertEquals("menu_regcode", svc.effectiveRegCodeMenuId(), "还没有用户端注册码菜单时看 menu_regcode");
        appMenu("/regcode", "menu_app_regcode", 0);
        assertEquals("menu_app_regcode", svc.effectiveRegCodeMenuId());
        assertFalse(svc.canUseRegCode(u("genAdmin", "role_gen_admin", null)), "同步后只认用户端注册码菜单，只有 menu_regcode 不行");
        assertTrue(svc.canUseRegCode(u("gen", "role_app_gen", null)));
        u("admin", "role_admin", null);
        SysUsers cust = u("cust", "role_app_gen", "admin");
        assertTrue(svc.canUseRegCode(u("sub", "role_x", "cust")), "底层子用户跟着创建人");
        assertTrue(cust.getParentId() != null);

        appMenu("/regcode", "menu_app_regcode", 1);
        assertEquals(null, svc.effectiveRegCodeMenuId(), "用户端注册码菜单已停用");
        assertFalse(svc.canUseRegCode(u("gen", "role_app_gen", null)), "菜单停用后谁都不能用");
        assertTrue(svc.canUseRegCode(u("root", "role_root", null)), "ROOT 除外");
    }

    @Test
    void webBlockedMessageMatchesAccountKind() {
        u("root", "role_root", null);
        u("admin", "role_admin", null);
        assertEquals("注册码客户请使用手机端登录", svc.webBlockedMessage(u("cRoot", "role_regcode_client", "root")),
                "ROOT 建的注册码客户（有 parent_id）是客户，不是子用户");
        assertEquals("注册码客户请使用手机端登录", svc.webBlockedMessage(u("cAdmin", "role_regcode_client", "admin")),
                "管理员建的注册码客户（有 parent_id）是客户，不是子用户");
        assertEquals("注册码客户请使用手机端登录", svc.webBlockedMessage(u("c", "role_regcode_client", null)));
        assertEquals("子用户请使用手机端登录，仅可生成注册码", svc.webBlockedMessage(u("s", "role_regcode_client", "cAdmin")),
                "创建人是注册码客户：子用户");
        assertEquals("子用户请使用手机端登录，仅可生成注册码", svc.webBlockedMessage(u("s1", "role_x", "c")),
                "创建人是注册码客户（子用户自己不是客户角色）：子用户");
        assertEquals("子用户请使用手机端登录，仅可生成注册码", svc.webBlockedMessage(u("s2", "role_x", "gone")),
                "创建人已不存在：按子用户");
        assertEquals("该账号请使用手机端登录", svc.webBlockedMessage(u("adminSub", "role_x", "admin")),
                "管理员名下的普通子账号：中性提示");
        assertEquals("该账号请使用手机端登录", svc.webBlockedMessage(u("m", "role_admin", null)));
    }

    /** createdCount（/auth/me）和 max_sub_users 上限（新建 / 重新启用）只数启用中的注册码子用户，螃蟹出货等其他子账号不算 */
    @Test
    @SuppressWarnings("unchecked")
    void enabledSubUserCountOnlyCountsEnabledRegCodeSubUsers() {
        when(regCodeUsers.list(any(Wrapper.class))).thenAnswer(inv -> {
            AbstractWrapper<?, ?, ?> w = inv.getArgument(0);
            w.getSqlSegment();
            List<RegCodeUser> rows = new java.util.ArrayList<>();
            for (Object v : w.getParamNameValuePairs().values()) {
                String id = String.valueOf(v);
                if (status.containsKey(id)) {
                    RegCodeUser a = new RegCodeUser();
                    a.setUserId(id);
                    a.setStatus(status.get(id));
                    rows.add(a);
                }
            }
            return rows;
        });
        status.put("rcOn", 1);
        status.put("rcOff", 0);
        status.put("crabSub", 1);
        status.put("crabSubOff", 0);
        List<SysUsers> ownerChildren = List.of(
                u("rcOn", "role_regcode_client", "owner"),
                u("rcNoRow", " role_regcode_client ", "owner"),   // 没有 reg_code_user 行 = 启用
                u("rcOff", "role_regcode_client", "owner"),       // 停用：不算
                u("crabSub", "role_crab", "owner"),               // 螃蟹出货子账号：不算
                u("crabSubOff", "role_crab", "owner"),
                u("plainSub", null, "owner"));                    // 没有角色的子账号：不算
        when(users.list(any(Wrapper.class))).thenReturn(ownerChildren);
        assertEquals(2, svc.enabledSubUserCount("owner"));

        // 只有螃蟹 / 其他子账号（如 t_crabMain）：0，而且不用再查 reg_code_user
        org.mockito.Mockito.clearInvocations(regCodeUsers);
        List<SysUsers> crabChildren = List.of(
                u("c1", "role_crab", "crabMain"), u("c2", "role_ops", "crabMain"), u("c3", "role_crab", "crabMain"));
        when(users.list(any(Wrapper.class))).thenReturn(crabChildren);
        assertEquals(0, svc.enabledSubUserCount("crabMain"));
        verify(regCodeUsers, never()).list(any(Wrapper.class));

        when(users.list(any(Wrapper.class))).thenReturn(List.of());
        assertEquals(0, svc.enabledSubUserCount("nobody"));
        assertEquals(0, svc.enabledSubUserCount(" "));
        assertTrue(RegCodeAccessService.isRegCodeRole(" role_regcode_client"));
        assertFalse(RegCodeAccessService.isRegCodeRole("role_crab"));
        assertFalse(RegCodeAccessService.isRegCodeRole(null));
    }
}
