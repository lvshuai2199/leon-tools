package springboot.service.menu;

import org.junit.jupiter.api.Test;
import springboot.domain.SysMenus;
import springboot.service.impl.SysRoleMenuServiceImpl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
