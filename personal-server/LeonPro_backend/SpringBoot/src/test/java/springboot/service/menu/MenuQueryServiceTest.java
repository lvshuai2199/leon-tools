package springboot.service.menu;

import org.junit.jupiter.api.Test;
import springboot.domain.SysMenus;
import springboot.service.impl.SysRoleMenuServiceImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MenuQueryServiceTest {

    private static SysMenus m(String id, String parent, int sort, int visible) {
        SysMenus x = new SysMenus();
        x.setId(id);
        x.setParentId(parent);
        x.setSortOrder(sort);
        x.setVisible(visible);
        x.setMenuType(1);
        x.setClient("app");
        x.setDisabled(0);
        return x;
    }

    private static List<String> ids(List<SysMenus> list) {
        return list.stream().map(SysMenus::getId).toList();
    }

    private final List<SysMenus> app = List.of(
            m("crab", "0", 1, 1),
            m("crab_new", "crab", 1, 0),
            m("crab_id", "crab", 2, 0),
            m("crab_stats", "crab", 3, 1),
            m("regcode", "0", 2, 1),
            m("tools", "0", 3, 1),
            m("tools_x", "tools", 1, 1));

    @Test
    void rootGetsAllSorted() {
        assertEquals(List.of("crab", "crab_new", "tools_x", "crab_id", "regcode", "crab_stats", "tools"),
                ids(MenuQueryService.visibleMenus(app, List.of(), true)));
    }

    @Test
    void grantedPageBringsHiddenDescendantsButNotVisibleOnes() {
        assertEquals(Set.of("crab", "crab_new", "crab_id"),
                Set.copyOf(ids(MenuQueryService.visibleMenus(app, List.of("crab"), false))));
    }

    @Test
    void ancestorsAddedAndUnknownGrantsIgnored() {
        assertEquals(Set.of("tools", "tools_x"),
                Set.copyOf(ids(MenuQueryService.visibleMenus(app, List.of("tools_x", "menu_crab", "gone"), false))));
        assertEquals(List.of(), MenuQueryService.visibleMenus(app, List.of(), false));
    }

    @Test
    void disabledParentNotAdded() {
        // available 已过滤掉停用的 tools
        List<SysMenus> withoutTools = app.stream().filter(x -> !x.getId().equals("tools")).toList();
        assertEquals(List.of("tools_x"), ids(MenuQueryService.visibleMenus(withoutTools, List.of("tools_x"), false)));
    }

    @Test
    void roleSaveKeepsDisabledAndOtherClientGrants() {
        SysMenus adminA = m("a", "0", 1, 1);
        adminA.setClient("admin");
        SysMenus adminOff = m("off", "0", 2, 1);
        adminOff.setClient("admin");
        adminOff.setDisabled(1);
        SysMenus appCrab = m("crab", "0", 1, 1);
        SysMenus appReg = m("regcode", "0", 2, 1);
        Map<String, SysMenus> menus = List.of(adminA, adminOff, appCrab, appReg).stream()
                .collect(Collectors.toMap(SysMenus::getId, x -> x));
        List<String> existing = List.of("a", "off", "crab", "missing");

        // 按 admin 端保存：取消 a；app 端授权和停用菜单授权保留；提交里的 app id 忽略；不存在的 id 丢弃
        assertEquals(Set.of("off", "crab"),
                Set.copyOf(SysRoleMenuServiceImpl.mergeGrants(existing, List.of("regcode"), menus, "admin")));
        // 不传 client：整体替换，但停用菜单授权保留
        assertEquals(Set.of("off", "regcode"),
                Set.copyOf(SysRoleMenuServiceImpl.mergeGrants(existing, List.of("regcode", "nope"), menus, null)));
        // 按 app 端保存
        assertEquals(Set.of("a", "off", "regcode"),
                Set.copyOf(SysRoleMenuServiceImpl.mergeGrants(existing, List.of("regcode"), menus, "app")));
    }

    private static SysMenus page(String id, String parent, String url, String routeKey) {
        SysMenus x = m(id, parent, 1, 1);
        x.setMenuUrl(url);
        x.setRouteKey(routeKey);
        return x;
    }

    private static Map<String, String> keys(List<SysMenus> list) {
        Map<String, String> out = new HashMap<>();
        list.forEach(x -> out.put(x.getId(), x.getRouteKey()));
        return out;
    }

    /** appMenus / 管理端菜单的 routeKey 永远是完整路径（/crab/new、/crab/:id），不是相对段 */
    @Test
    void routeKeysAreAlwaysFullPaths() {
        SysMenus toolsOff = page("tools", "0", "/tools", "/tools/");
        toolsOff.setDisabled(1);
        List<SysMenus> rows = new ArrayList<>(List.of(
                page("crab", "0", "/crab", "/crab"),
                page("crab_new", "crab", "new", null),            // 还没回填 route_key
                page("crab_id", "crab", ":id", "/crab/:id"),
                page("crab_rel", "crab", "stats", "stats"),       // 误存成相对段
                page("deep", "crab_new", "step2", null),          // 两级相对
                page("tool_x", "tools", "x", null),               // 父级已停用，只在 lookup 里
                page("orphan", "gone", "lost", null),             // 拼不出：置空，不返回相对段
                page("reg", "0", "regcode", null),                // 顶级缺 /
                page("ext", "0", "https://example.com/a", null),
                page("dir", "0", "", null)));
        MenuQueryService.fillFullRouteKeys(rows, List.of(toolsOff));
        Map<String, String> k = keys(rows);
        assertEquals("/crab", k.get("crab"));
        assertEquals("/crab/new", k.get("crab_new"));
        assertEquals("/crab/:id", k.get("crab_id"));
        assertEquals("/crab/stats", k.get("crab_rel"));
        assertEquals("/crab/new/step2", k.get("deep"));
        assertEquals("/tools/x", k.get("tool_x"));
        assertEquals(null, k.get("orphan"));
        assertEquals("/regcode", k.get("reg"));
        assertEquals("https://example.com/a", k.get("ext"));
        assertEquals(null, k.get("dir"));
        rows.forEach(r -> assertTrue(r.getRouteKey() == null || r.getRouteKey().startsWith("/")
                || r.getRouteKey().startsWith("https://"), r.getId() + " → " + r.getRouteKey()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void enabledMenusReturnsFullRouteKeys() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, SysMenus.class);
        SysMenus crab = page("crab", "0", "/crab", "/crab");
        SysMenus crabNew = page("crab_new", "crab", "new", null);
        SysMenus crabId = page("crab_id", "crab", ":id", "/crab/:id");
        SysMenus toolsOff = page("tools", "0", "/tools", "/tools");
        toolsOff.setDisabled(1);
        SysMenus toolX = page("tool_x", "tools", "x", null);
        springboot.service.SysMenusService menus = mock(springboot.service.SysMenusService.class);
        when(menus.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(new ArrayList<>(List.of(crab, crabNew, crabId, toolX)), List.of(crab, crabNew, crabId, toolsOff, toolX));
        MenuQueryService q = new MenuQueryService(menus, mock(springboot.service.SysRoleMenuService.class),
                mock(springboot.service.SysRolesService.class));
        Map<String, String> k = keys(q.enabledMenus("app"));
        assertEquals(Map.of("crab", "/crab", "crab_new", "/crab/new", "crab_id", "/crab/:id", "tool_x", "/tools/x"), k);
        // 全部已有完整 route_key 时不多查一次
        springboot.service.SysMenusService menus2 = mock(springboot.service.SysMenusService.class);
        when(menus2.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(new ArrayList<>(List.of(page("crab", "0", "/crab", "/crab"), page("crab_id", "crab", ":id", "/crab/:id"))));
        MenuQueryService q2 = new MenuQueryService(menus2, mock(springboot.service.SysRoleMenuService.class),
                mock(springboot.service.SysRolesService.class));
        assertEquals(Map.of("crab", "/crab", "crab_id", "/crab/:id"), keys(q2.enabledMenus("admin")));
        org.mockito.Mockito.verify(menus2, org.mockito.Mockito.times(1)).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }
}
