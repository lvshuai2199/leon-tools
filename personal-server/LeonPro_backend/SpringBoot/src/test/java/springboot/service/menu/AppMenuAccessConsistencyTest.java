package springboot.service.menu;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import springboot.domain.RegCodeUser;
import springboot.domain.SysMenus;
import springboot.domain.SysRoles;
import springboot.domain.SysUsers;
import springboot.service.RegCodeAccessService;
import springboot.service.RegCodeConfigService;
import springboot.service.RegCodeUserConfigService;
import springboot.service.RegCodeUserService;
import springboot.service.SysMenusService;
import springboot.service.SysRoleMenuService;
import springboot.service.SysRolesService;
import springboot.service.SysUsersService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * /auth/me 的 appMenus 必须和接口权限判断一致：出货菜单 ⇔ canUseCrab（/app/crabShipment/**），
 * 注册码菜单 ⇔ canUseRegCode（/common/**）。用有代表性的角色 / 账号逐个核对，并写明每个人的预期结果。
 */
class AppMenuAccessConsistencyTest {

    SysUsersService users;
    SysRolesService roles;
    SysRoleMenuService roleMenus;
    RegCodeUserService regCodeUsers;
    SysMenusService sysMenus;
    MenuQueryService menuQuery;
    RegCodeAccessService access;
    AppMenuAccessService appMenus;

    /** 库里的用户端菜单（含停用的），按 id */
    Map<String, SysMenus> appRows = new LinkedHashMap<>();
    Map<String, List<String>> grants = new HashMap<>();
    Map<String, Integer> status = new HashMap<>();
    Map<String, SysUsers> userRows = new HashMap<>();

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUsers.class);
        TableInfoHelper.initTableInfo(assistant, RegCodeUser.class);
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
        menuQuery = mock(MenuQueryService.class);
        access = new RegCodeAccessService(users, roles, roleMenus, sysMenus, regCodeUsers,
                mock(RegCodeUserConfigService.class), mock(RegCodeConfigService.class));
        appMenus = new AppMenuAccessService(menuQuery, access, roleMenus);

        when(users.getById(anyString())).thenAnswer(inv -> userRows.get((String) inv.getArgument(0)));
        when(roleMenus.getMenuIdsByRole(anyString())).thenAnswer(inv -> grants.getOrDefault((String) inv.getArgument(0), List.of()));
        when(menuQuery.enabledMenus(MenuClients.APP)).thenAnswer(inv -> appRows.values().stream()
                .filter(m -> m.getDisabled() == null || m.getDisabled() == 0).collect(Collectors.toList()));
        // RegCodeAccessService 按 client=app + route_key 查菜单（包括已停用的）
        when(sysMenus.list(any(Wrapper.class))).thenAnswer(inv -> {
            AbstractWrapper<?, ?, ?> w = inv.getArgument(0);
            w.getSqlSegment();
            for (Object v : w.getParamNameValuePairs().values()) {
                for (SysMenus m : appRows.values()) {
                    if (String.valueOf(v).equals(m.getRouteKey())) {
                        return List.of(m);
                    }
                }
            }
            return List.of();
        });
        when(regCodeUsers.getOne(any(Wrapper.class), anyBoolean())).thenAnswer(inv -> {
            AbstractWrapper<?, ?, ?> w = inv.getArgument(0);
            w.getSqlSegment();
            for (Object v : w.getParamNameValuePairs().values()) {
                if (status.containsKey(String.valueOf(v))) {
                    RegCodeUser a = new RegCodeUser();
                    a.setUserId(String.valueOf(v));
                    a.setStatus(status.get(String.valueOf(v)));
                    return a;
                }
            }
            return null;
        });

        // 与 frontend_phone/src/router/menus.json 一致的用户端菜单，外加一个“其他”菜单
        menu("menu_app_crab", "/crab", null, 1);
        menu("menu_app_crab_new", "/crab/new", "menu_app_crab", 0);
        menu("menu_app_crab_id", "/crab/:id", "menu_app_crab", 0);
        menu("menu_app_badminton", "/badminton", null, 1);
        menu("menu_app_badminton_new", "/badminton/new", "menu_app_badminton", 0);
        menu("menu_app_badminton_id", "/badminton/:id", "menu_app_badminton", 0);
        menu("menu_app_regcode", "/regcode", null, 1);
        menu("menu_app_other", "/other", null, 1);

        grants.put("role_ops", List.of("menu_app_crab", "menu_app_badminton", "menu_app_regcode"));
        grants.put("role_crab", List.of("menu_app_crab", "menu_app_badminton"));
        grants.put("role_gen", List.of("menu_app_regcode", "menu_app_other"));
        grants.put("role_regcode_client", List.of("menu_app_regcode", "menu_app_crab", "menu_app_badminton"));
        grants.put("role_sub_all", List.of("menu_app_crab", "menu_app_badminton", "menu_app_regcode", "menu_app_other"));
        grants.put("role_none", List.of());
        grants.put("role_admin_legacy", List.of("menu_crab", "menu_badminton", "menu_regcode", "menu_user"));

        user("root", "role_root", null);
        user("ops", "role_ops", null);
        user("crabber", "role_crab", null);
        user("gen", "role_gen", null);
        user("legacy", "role_admin_legacy", null);
        user("rcTop", "role_regcode_client", null);
        user("rcByOps", "role_regcode_client", "ops");
        user("rcByRoot", "role_regcode_client", "root");
        user("subOfOps", "role_none", "ops");
        user("subOfCrabber", "role_sub_all", "crabber");
        user("subOfGen", "role_none", "gen");
        user("subOfRcTop", "role_regcode_client", "rcTop");
        user("subOfRcByOps", "role_none", "rcByOps");
        user("subOfRoot", "role_none", "root");
        user("orphan", "role_sub_all", "gone");
        user("nobody", "role_none", null);
    }

    private void menu(String id, String route, String parentId, int visible) {
        SysMenus m = new SysMenus();
        m.setId(id);
        m.setClient("app");
        m.setRouteKey(route);
        m.setMenuUrl(route);
        m.setParentId(parentId);
        m.setVisible(visible);
        m.setMenuType(1);
        m.setDisabled(0);
        m.setSortOrder(appRows.size());
        appRows.put(id, m);
    }

    private void user(String id, String roleId, String parentId) {
        SysUsers u = new SysUsers();
        u.setId(id);
        u.setUsername(id);
        u.setRoleId(roleId);
        u.setParentId(parentId);
        userRows.put(id, u);
    }

    private Set<String> routes(String userId) {
        return appMenus.appMenusFor(userRows.get(userId)).stream().map(SysMenus::getRouteKey).collect(Collectors.toSet());
    }

    /** 每个账号：appMenus 里的出货 / 注册码入口与 canUseCrab / canUseRegCode 完全一致，出货的隐藏子页跟着出货走 */
    private void assertConsistentForAll() {
        List<String> problems = new ArrayList<>();
        for (SysUsers u : userRows.values()) {
            Set<String> r = routes(u.getId());
            boolean crab = access.canUseCrab(u);
            boolean badminton = access.canUseBadminton(u);
            boolean reg = access.canUseRegCode(u);
            if (r.contains("/crab") != crab || r.contains("/crab/new") != crab || r.contains("/crab/:id") != crab) {
                problems.add(u.getId() + " 出货: canUseCrab=" + crab + " appMenus=" + r);
            }
            if (r.contains("/badminton") != badminton || r.contains("/badminton/new") != badminton
                    || r.contains("/badminton/:id") != badminton) {
                problems.add(u.getId() + " 羽毛球: canUseBadminton=" + badminton + " appMenus=" + r);
            }
            if (r.contains("/regcode") != reg) {
                problems.add(u.getId() + " 注册码: canUseRegCode=" + reg + " appMenus=" + r);
            }
        }
        assertEquals(List.of(), problems);
    }

    private void expect(String userId, boolean crab, boolean badminton, boolean reg) {
        SysUsers u = userRows.get(userId);
        assertEquals(crab, access.canUseCrab(u), userId + " 出货权限");
        assertEquals(badminton, access.canUseBadminton(u), userId + " 羽毛球权限");
        assertEquals(reg, access.canUseRegCode(u), userId + " 注册码权限");
        Set<String> r = routes(userId);
        assertEquals(crab, r.contains("/crab"), userId + " appMenus 出货入口 " + r);
        assertEquals(badminton, r.contains("/badminton"), userId + " appMenus 羽毛球入口 " + r);
        assertEquals(reg, r.contains("/regcode"), userId + " appMenus 注册码入口 " + r);
    }

    @Test
    void representativeRolesMatchPermissionChecks() {
        expect("root", true, true, true);
        expect("ops", true, true, true);
        expect("crabber", true, true, false);
        expect("gen", false, false, true);
        expect("legacy", false, false, false);
        expect("rcTop", false, false, true);
        expect("rcByOps", false, false, true);
        expect("rcByRoot", false, false, true);
        expect("subOfOps", true, true, false);
        expect("subOfCrabber", true, true, true);
        expect("subOfGen", false, false, false);
        expect("subOfRcTop", false, false, true);
        expect("subOfRcByOps", false, false, true);
        expect("subOfRoot", true, true, false);
        expect("orphan", false, false, false);
        expect("nobody", false, false, false);
        assertConsistentForAll();
    }

    @Test
    void otherAppMenusFollowGoverningRole() {
        assertEquals(Set.of("/regcode", "/other"), routes("gen"));
        assertEquals(Set.of("/other"), routes("subOfGen"), "子账号的其他菜单看创建人的角色，创建人的注册码授权不会带过来");
        assertEquals(Set.of(), routes("orphan"), "创建人已删除：自己角色有也不给");
        assertEquals(Set.of("/crab", "/crab/new", "/crab/:id", "/badminton", "/badminton/new", "/badminton/:id",
                "/regcode", "/other"), routes("root"));
    }

    @Test
    void stateChangesKeepMenusAndChecksInStep() {
        // 创建人失去出货菜单
        grants.put("role_crab", List.of());
        expect("subOfCrabber", false, false, true);
        grants.put("role_crab", List.of("menu_app_crab", "menu_app_badminton"));
        // 创建人账号被停用
        status.put("rcTop", 0);
        expect("subOfRcTop", false, false, false);
        status.remove("rcTop");
        // 创建人角色被禁用
        SysRoles off = new SysRoles();
        off.setId("role_ops");
        off.setIsDisabled(1);
        when(roles.getById("role_ops")).thenReturn(off);
        expect("subOfOps", false, false, false);
        when(roles.getById("role_ops")).thenReturn(null);
        // 用户端出货菜单停用：除 ROOT 外都没有出货，也没有入口
        appRows.get("menu_app_crab").setDisabled(1);
        expect("ops", false, true, true);
        expect("subOfOps", false, true, false);
        // ROOT 的接口权限不受菜单停用影响（ROOT 拥有全部权限），但停用的菜单不会出现在任何人的 appMenus 里
        assertEquals(true, access.canUseCrab(userRows.get("root")));
        assertEquals(Set.of("/badminton", "/badminton/new", "/badminton/:id", "/regcode", "/other"), routes("root"),
                "出货菜单停用后它的隐藏子页也不返回");
        // 角色直接勾了子页（/crab/new）也不能在出货菜单停用时单独出现
        grants.put("role_gen", List.of("menu_app_regcode", "menu_app_other", "menu_app_crab_new"));
        assertEquals(Set.of("/regcode", "/other"), routes("gen"));
        grants.put("role_gen", List.of("menu_app_regcode", "menu_app_other"));
        appRows.get("menu_app_crab").setDisabled(0);
        // 用户端注册码菜单停用
        appRows.get("menu_app_regcode").setDisabled(1);
        expect("gen", false, false, false);
        expect("subOfRcTop", false, false, false);
        appRows.get("menu_app_regcode").setDisabled(0);
        assertConsistentForAll();
    }

    /** 注册码子用户：appMenus 只有 /regcode，不管自己的角色勾了出货 / 其他菜单；创建人失去注册码权限后什么都没有 */
    @Test
    void regCodeSubUserAppMenusAreExactlyRegcode() {
        user("subAllOfRcTop", "role_sub_all", "rcTop");
        assertEquals(Set.of("/regcode"), routes("subOfRcTop"));
        assertEquals(Set.of("/regcode"), routes("subOfRcByOps"));
        assertEquals(Set.of("/regcode"), routes("subAllOfRcTop"), "角色勾了出货和其他菜单也只有 /regcode");
        expect("subAllOfRcTop", false, false, true);

        // 不推荐的组合：出货主账号 both 又有注册码账号，它在注册码页建的 bothR
        user("both", "role_ops", null);
        user("bothR", "role_sub_all", "both");
        user("bothC", "role_none", "both");
        status.put("both", 1);
        status.put("bothR", 1);
        assertEquals(Set.of("/regcode"), routes("bothR"));
        expect("bothR", false, false, true);
        expect("bothC", true, true, false);
        grants.put("role_ops", List.of("menu_app_crab", "menu_app_badminton"));
        expect("bothR", false, false, false);
        assertEquals(Set.of(), routes("bothR"), "创建人去掉 /regcode：子用户没有任何菜单");
        grants.put("role_ops", List.of("menu_app_crab", "menu_app_badminton", "menu_app_regcode"));
        assertConsistentForAll();
    }
}
