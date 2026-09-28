package springboot.service.menu;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import springboot.domain.SysMenus;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuSyncPlannerTest {

    private static final MenuSyncPlanner.Options APPLY = new MenuSyncPlanner.Options(true, true);

    private static SysMenus row(String id, String parentId, String name, String url, String component, int type,
                                int sort, String routeName) {
        SysMenus m = new SysMenus();
        m.setId(id);
        m.setParentId(parentId);
        m.setMenuName(name);
        m.setMenuUrl(url);
        m.setComponent(component);
        m.setMenuType(type);
        m.setSortOrder(sort);
        m.setRouteName(routeName);
        m.setVisible(1);
        m.setKeepAlive(0);
        m.setAlwaysShow(0);
        m.setClient("admin");
        m.setManaged(0);
        m.setDisabled(0);
        return m;
    }

    /** 现有库（MenuDataSeeder 种子）的一部分，route_key 全空、都是手工菜单 */
    private static List<SysMenus> seededAdminRows() {
        List<SysMenus> rows = new ArrayList<>();
        SysMenus tool = row("menu_tool", "0", "工具中心", "/tool", "Layout", 0, 1, null);
        tool.setIcon("api");
        tool.setRedirect("/tool/trace");
        rows.add(tool);
        SysMenus trace = row("menu_trace", "menu_tool", "轨迹分析", "trace", "tool/trace/index", 1, 1, "Trace");
        trace.setIcon("code");
        rows.add(trace);
        SysMenus center = row("menu_regcode_center", "0", "注册码", "/regcode", "Layout", 0, 2, "RegCodeCenter");
        center.setIcon("key");
        center.setRedirect("/regcode/generate");
        center.setAlwaysShow(1);
        rows.add(center);
        SysMenus gen = row("menu_regcode", "menu_regcode_center", "注册码生成", "generate", "tool/regcode/index", 1, 1, "RegCode");
        gen.setIcon("key");
        rows.add(gen);
        SysMenus rcUser = row("menu_regcode_user", "menu_regcode_center", "注册码用户", "user", "tool/regcode-user/index", 1, 3, "RegCodeUser");
        rcUser.setIcon("user");
        rcUser.setKeepAlive(1);
        rows.add(rcUser);
        SysMenus work = row("menu_work", "0", "业务管理", "/work", "Layout", 0, 3, null);
        work.setIcon("todo");
        work.setRedirect("/work/tasks");
        rows.add(work);
        SysMenus crab = row("menu_crab", "menu_work", "螃蟹出货", "crab", "work/crab/index", 1, 2, "CrabShipment");
        crab.setIcon("table");
        rows.add(crab);
        SysMenus system = row("menu_system", "0", "系统管理", "/system", "Layout", 0, 4, null);
        system.setIcon("system");
        system.setRedirect("/system/user");
        rows.add(system);
        SysMenus user = row("menu_user", "menu_system", "用户管理", "user", "system/user/index", 1, 1, "User");
        user.setIcon("role");
        user.setKeepAlive(1);
        rows.add(user);
        // 手工菜单（UUID），清单里没有
        rows.add(row("3f2a9c", "menu_tool", "临时工具", "tmp", "tool/tmp/index", 1, 9, "Tmp"));
        // 按钮不参与
        rows.add(row("btn1", "menu_user", "新增按钮", "", null, 2, 1, null));
        return rows;
    }

    private static List<MenuManifestEntry> sample(String client) {
        MenuManifest m = MenuManifestLoader.load(client, new ClassPathResource("menu-samples/" + client + "-menus.json"));
        assertTrue(m.loaded(), String.valueOf(m.errors()));
        return m.entries();
    }

    private static Set<String> ids(List<SysMenus> rows) {
        return rows.stream().map(SysMenus::getId).collect(Collectors.toCollection(HashSet::new));
    }

    @Test
    void firstSyncOnExistingDbKeepsIdsAndClaimsRows() {
        List<SysMenus> rows = seededAdminRows();
        MenuSyncPlan plan = MenuSyncPlanner.plan("admin", sample("admin"), rows, ids(rows), APPLY, new Date());

        // 两个 menu_url=user 靠完整路径区分
        Map<String, String> backfilled = plan.getBackfills().stream()
                .collect(Collectors.toMap(MenuSyncPlan.Backfill::id, MenuSyncPlan.Backfill::routeKey));
        assertEquals("/regcode/user", backfilled.get("menu_regcode_user"));
        assertEquals("/system/user", backfilled.get("menu_user"));
        assertEquals("/tool/tmp", backfilled.get("3f2a9c"));
        assertFalse(backfilled.containsKey("btn1"));

        // 清单里只有壁纸是新页面，插入时用历史 id
        assertEquals(1, plan.getInserts().size());
        SysMenus wallpaper = plan.getInserts().get(0);
        assertEquals("menu_wallpaper", wallpaper.getId());
        assertEquals("menu_tool", wallpaper.getParentId());
        assertEquals("wallpaper", wallpaper.getMenuUrl());
        assertEquals("/tool/wallpaper", wallpaper.getRouteKey());
        assertEquals(1, wallpaper.getManaged());

        // 其余 9 条都被认领（managed 0 → 1），原 id 不变
        assertEquals(9, plan.claimedCount());
        Set<String> updatedIds = plan.getUpdates().stream().map(MenuSyncPlan.Update::id).collect(Collectors.toSet());
        assertTrue(updatedIds.containsAll(Set.of("menu_tool", "menu_regcode_user", "menu_crab", "menu_user")));
        // 业务管理的 redirect 按清单改为 /work/crab
        MenuSyncPlan.Update work = plan.getUpdates().stream().filter(u -> u.id().equals("menu_work")).findFirst().orElseThrow();
        assertEquals("/work/crab", work.changes().get("redirect"));
        // 名称、排序没变的只改 managed
        MenuSyncPlan.Update trace = plan.getUpdates().stream().filter(u -> u.id().equals("menu_trace")).findFirst().orElseThrow();
        assertEquals(Map.of("managed", 1), trace.changes());

        assertTrue(plan.getDisables().isEmpty());
        // 手工菜单 3f2a9c 与按钮 btn1 未处理
        assertEquals(2, plan.getManualUntouched());
    }

    /** 模拟第一次 apply 之后的库状态，再用同一份清单同步：什么都不写 */
    @Test
    void secondRunIsNoop() {
        List<SysMenus> rows = seededAdminRows();
        Date now = new Date();
        MenuSyncPlan first = MenuSyncPlanner.plan("admin", sample("admin"), rows, ids(rows), APPLY, now);
        List<SysMenus> after = applyInMemory(rows, first);
        MenuSyncPlan second = MenuSyncPlanner.plan("admin", sample("admin"), after, ids(after), APPLY, now);
        assertFalse(second.hasWrites(), second.summary() + " " + second.getUpdates());
        assertEquals(10, second.getUnchanged());
    }

    @Test
    void removedEntryIsDisabledAndReenabledWhenAddedBack() {
        List<SysMenus> rows = seededAdminRows();
        Date now = new Date();
        List<SysMenus> after = applyInMemory(rows, MenuSyncPlanner.plan("admin", sample("admin"), rows, ids(rows), APPLY, now));

        List<MenuManifestEntry> withoutCrab = new ArrayList<>(sample("admin"));
        withoutCrab.removeIf(e -> e.getPath().equals("/work/crab"));
        MenuSyncPlan removed = MenuSyncPlanner.plan("admin", withoutCrab, after, ids(after), APPLY, now);
        assertEquals(1, removed.getDisables().size());
        assertEquals("menu_crab", removed.getDisables().get(0).id());
        assertTrue(removed.getInserts().isEmpty());

        // disable-missing=false（开发环境）：不停用，只记录
        MenuSyncPlan dev = MenuSyncPlanner.plan("admin", withoutCrab, after, ids(after),
                new MenuSyncPlanner.Options(false, true), now);
        assertTrue(dev.getDisables().isEmpty());
        assertEquals(1, dev.getDisablesSkipped().size());

        List<SysMenus> disabled = applyInMemory(after, removed);
        MenuSyncPlan back = MenuSyncPlanner.plan("admin", sample("admin"), disabled, ids(disabled), APPLY, now);
        assertEquals(1, back.reenabledCount());
        MenuSyncPlan.Update u = back.getUpdates().get(0);
        assertEquals("menu_crab", u.id());
        assertEquals(0, u.changes().get("disabled"));
    }

    @Test
    void guardRefusesMassDisable() {
        List<SysMenus> rows = seededAdminRows();
        Date now = new Date();
        List<SysMenus> after = applyInMemory(rows, MenuSyncPlanner.plan("admin", sample("admin"), rows, ids(rows), APPLY, now));
        List<MenuManifestEntry> tiny = new ArrayList<>(sample("admin").subList(0, 2));
        MenuSyncPlan plan = MenuSyncPlanner.plan("admin", tiny, after, ids(after), APPLY, now);
        assertTrue(plan.isDisableBlockedByGuard());
        assertTrue(plan.getDisables().isEmpty());
        assertEquals(8, plan.getDisablesSkipped().size());
        MenuSyncPlan unguarded = MenuSyncPlanner.plan("admin", tiny, after, ids(after), new MenuSyncPlanner.Options(true, false), now);
        assertEquals(8, unguarded.getDisables().size());
    }

    @Test
    void duplicateBackfillKeepsNull() {
        List<SysMenus> rows = new ArrayList<>();
        rows.add(row("a", "0", "A", "/x", "Layout", 0, 1, null));
        rows.add(row("b", "0", "B", "x", "Layout", 0, 2, null));
        MenuSyncPlan plan = MenuSyncPlanner.plan("admin", List.of(), rows, ids(rows), APPLY, new Date());
        assertEquals(1, plan.getBackfills().size());
        assertEquals("a", plan.getBackfills().get(0).id());
        assertNull(rows.get(1).getRouteKey());
        assertTrue(plan.getWarnings().stream().anyMatch(w -> w.contains("重复")));
    }

    @Test
    void appMenusInsertedWithDerivedIdsOnFreshDb() {
        MenuSyncPlan plan = MenuSyncPlanner.plan("app", sample("app"), List.of(), Set.of("menu_crab"), APPLY, new Date());
        Map<String, SysMenus> byKey = plan.getInserts().stream().collect(Collectors.toMap(SysMenus::getRouteKey, m -> m));
        assertEquals("menu_app_crab", byKey.get("/crab").getId());
        assertEquals("menu_app_crab_id", byKey.get("/crab/:id").getId());
        assertEquals("menu_app_regcode", byKey.get("/regcode").getId());
        assertEquals("menu_app_crab", byKey.get("/crab/new").getParentId());
        assertEquals("new", byKey.get("/crab/new").getMenuUrl());
        assertEquals(0, byKey.get("/crab/new").getVisible());
        assertEquals("0", byKey.get("/crab").getParentId());
        assertEquals("app", byKey.get("/crab").getClient());
    }

    @Test
    void idFallsBackToHashWhenTakenOrTooLong() {
        String id = MenuSyncPlanner.deriveId("app", "/crab", Set.of("menu_app_crab"));
        assertTrue(id.startsWith("menu_") && id.length() == 29, id);
        String legacyTaken = MenuSyncPlanner.deriveId("admin", "/work/crab", Set.of("menu_crab"));
        assertEquals("menu_work_crab", legacyTaken);
        String longPath = "/" + "a".repeat(80);
        assertEquals(29, MenuSyncPlanner.deriveId("admin", longPath, Set.of()).length());
    }

    /** 按计划修改内存里的行，模拟 apply 之后再读库 */
    private static List<SysMenus> applyInMemory(List<SysMenus> rows, MenuSyncPlan plan) {
        List<SysMenus> out = new ArrayList<>();
        for (SysMenus r : rows) {
            SysMenus c = copy(r);
            plan.getBackfills().stream().filter(b -> b.id().equals(c.getId())).forEach(b -> c.setRouteKey(b.routeKey()));
            plan.getUpdates().stream().filter(u -> u.id().equals(c.getId())).forEach(u -> u.changes().forEach((k, v) -> set(c, k, v)));
            plan.getDisables().stream().filter(d -> d.id().equals(c.getId())).forEach(d -> c.setDisabled(1));
            out.add(c);
        }
        plan.getInserts().forEach(i -> out.add(copy(i)));
        return out;
    }

    private static void set(SysMenus m, String col, Object v) {
        switch (col) {
            case "menu_name" -> m.setMenuName((String) v);
            case "menu_url" -> m.setMenuUrl((String) v);
            case "parent_id" -> m.setParentId((String) v);
            case "sort_order" -> m.setSortOrder((Integer) v);
            case "menu_type" -> m.setMenuType((Integer) v);
            case "component" -> m.setComponent((String) v);
            case "visible" -> m.setVisible((Integer) v);
            case "keep_alive" -> m.setKeepAlive((Integer) v);
            case "icon" -> m.setIcon((String) v);
            case "redirect" -> m.setRedirect((String) v);
            case "route_name" -> m.setRouteName((String) v);
            case "always_show" -> m.setAlwaysShow((Integer) v);
            case "route_key" -> m.setRouteKey((String) v);
            case "managed" -> m.setManaged((Integer) v);
            case "disabled" -> m.setDisabled((Integer) v);
            default -> throw new IllegalArgumentException(col);
        }
    }

    private static SysMenus copy(SysMenus r) {
        SysMenus c = new SysMenus();
        c.setId(r.getId());
        c.setMenuName(r.getMenuName());
        c.setMenuUrl(r.getMenuUrl());
        c.setParentId(r.getParentId());
        c.setSortOrder(r.getSortOrder());
        c.setIcon(r.getIcon());
        c.setVisible(r.getVisible());
        c.setMenuType(r.getMenuType());
        c.setComponent(r.getComponent());
        c.setRouteName(r.getRouteName());
        c.setKeepAlive(r.getKeepAlive());
        c.setAlwaysShow(r.getAlwaysShow());
        c.setRedirect(r.getRedirect());
        c.setClient(r.getClient());
        c.setRouteKey(r.getRouteKey());
        c.setManaged(r.getManaged());
        c.setDisabled(r.getDisabled());
        return c;
    }
}
