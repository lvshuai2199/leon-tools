package springboot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import springboot.domain.SysMenus;
import springboot.service.RegCodeAccessService;
import springboot.service.menu.MenuClients;
import springboot.service.menu.MenuManifest;
import springboot.service.menu.MenuManifestLoader;
import springboot.service.menu.MenuPaths;
import springboot.service.menu.MenuSyncPlan;
import springboot.service.menu.MenuSyncPlanner;
import springboot.utils.RoleUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * 启动时把页面清单（jar 内 classpath:menus/admin/menus.json、classpath:menus/app/menus.json）同步到 sys_menus。
 * 规则见 menu-sync-design.md：按 (client, route_key) upsert、保留原 id、永不删除；清单里消失的 managed 行标记 disabled=1
 * （开发环境默认不停用）；清单文件不存在或校验失败时跳过该端，绝不当成空清单。
 * mode=dry-run 只打印计划不写库；mode=off 完全跳过。每端一个事务，任何异常只记日志、不影响启动。
 * <p>
 * 同步后执行一次性授权（各自记录标记，只执行一次）：用户端注册码菜单授予已有 menu_regcode 的角色；
 * 用户端出货菜单授予已有 menu_crab 的角色（注册码客户角色除外）。目标菜单还不存在时不执行、不记标记。
 */
@Slf4j
@Component
@Order(5)
public class MenuManifestSync implements CommandLineRunner {

    public static final String MODE_OFF = "off";
    public static final String MODE_DRY_RUN = "dry-run";
    public static final String MODE_APPLY = "apply";

    static final String LOCK_NAME = "leonpro_menu_sync";
    static final String MARKER_APP_REGCODE = "menu_first_grant_app_regcode";
    static final String MARKER_APP_CRAB = "menu_first_grant_app_crab";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ResourceLoader resourceLoader;

    @Value("${app.menu-sync.mode:apply}")
    String mode = MODE_APPLY;
    @Value("${app.menu-sync.disable-missing:true}")
    boolean disableMissing = true;
    @Value("${app.menu-sync.disable-guard:true}")
    boolean disableGuard = true;
    @Value("${app.menu-sync.admin-manifest:classpath:menus/admin/menus.json}")
    String adminManifest = "classpath:menus/admin/menus.json";
    @Value("${app.menu-sync.app-manifest:classpath:menus/app/menus.json}")
    String appManifest = "classpath:menus/app/menus.json";
    @Value("${app.menu-sync.first-grant.app-regcode-route:/regcode}")
    String appRegCodeRoute = "/regcode";
    @Value("${app.menu-sync.first-grant.app-crab-route:/crab}")
    String appCrabRoute = "/crab";

    public MenuManifestSync(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, ResourceLoader resourceLoader) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(String... args) {
        String m = mode == null ? MODE_APPLY : mode.trim().toLowerCase(Locale.ROOT);
        if (MODE_OFF.equals(m)) {
            log.info("菜单同步已关闭（app.menu-sync.mode=off）");
            return;
        }
        if (!MODE_DRY_RUN.equals(m) && !MODE_APPLY.equals(m)) {
            log.error("app.menu-sync.mode={} 无效（只能是 off / dry-run / apply），本次不同步菜单", mode);
            return;
        }
        boolean apply = MODE_APPLY.equals(m);
        try {
            if (!schemaReady()) {
                log.warn("sys_menus 缺少 client / route_key / managed / disabled 字段，本次不同步菜单");
                return;
            }
            syncClient(MenuClients.ADMIN, adminManifest, apply);
            MenuSyncPlan appPlan = syncClient(MenuClients.APP, appManifest, apply);
            Set<String> plannedAppKeys = new HashSet<>();
            if (appPlan != null && !apply) {
                appPlan.getInserts().forEach(i -> plannedAppKeys.add(MenuPaths.key(i.getRouteKey())));
            }
            grantOnce(MARKER_APP_REGCODE, appRegCodeRoute, MenuDataSeeder.MENU_REGCODE,
                    Set.of(RoleUtils.ROOT_ROLE_ID), apply, plannedAppKeys);
            grantOnce(MARKER_APP_CRAB, appCrabRoute, MenuDataSeeder.MENU_CRAB,
                    Set.of(RoleUtils.ROOT_ROLE_ID, RegCodeAccessService.ROLE_REGCODE_CLIENT_ID), apply, plannedAppKeys);
        } catch (Exception e) {
            log.error("菜单同步异常（不影响启动）：{}", e.getMessage(), e);
        }
    }

    boolean schemaReady() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'sys_menus' AND COLUMN_NAME IN ('client', 'route_key', 'managed', 'disabled')",
                Integer.class);
        return n != null && n == 4;
    }

    /** @return 本端的计划；清单缺失 / 无效 / 异常时返回 null */
    MenuSyncPlan syncClient(String client, String location, boolean apply) {
        MenuManifest manifest = MenuManifestLoader.load(client, resolve(location));
        String tag = "菜单同步[" + client + "]" + (apply ? "" : "[dry-run]");
        manifest.warnings().forEach(w -> log.warn("{} 清单提示：{}", tag, w));
        if (manifest.status() == MenuManifest.Status.MISSING) {
            log.warn("{} 清单 {} 不存在，跳过该端（不会停用任何菜单）", tag, location);
            return null;
        }
        if (manifest.status() == MenuManifest.Status.INVALID) {
            manifest.errors().forEach(err -> log.error("{} 清单错误：{}", tag, err));
            log.error("{} 清单 {} 校验失败，跳过该端，不做任何写入", tag, location);
            return null;
        }
        long started = System.currentTimeMillis();
        try {
            return tx.execute(status -> {
                boolean locked = false;
                if (apply) {
                    Integer got = jdbc.queryForObject("SELECT GET_LOCK(?, 10)", Integer.class, LOCK_NAME);
                    if (got == null || got != 1) {
                        log.warn("{} 未拿到同步锁（可能另一实例正在同步），跳过", tag);
                        return null;
                    }
                    locked = true;
                }
                try {
                    List<SysMenus> rows = jdbc.query("SELECT * FROM sys_menus WHERE client = ?",
                            new BeanPropertyRowMapper<>(SysMenus.class), client);
                    Set<String> allIds = new HashSet<>(jdbc.queryForList("SELECT id FROM sys_menus", String.class));
                    Date now = new Date();
                    MenuSyncPlan plan = MenuSyncPlanner.plan(client, manifest.entries(), rows, allIds,
                            new MenuSyncPlanner.Options(disableMissing, disableGuard), now);
                    logPlan(tag, plan);
                    if (apply && plan.hasWrites()) {
                        execute(plan, now);
                    }
                    log.info("{} {}；耗时 {}ms", tag, plan.summary(), System.currentTimeMillis() - started);
                    return plan;
                } finally {
                    if (locked) {
                        jdbc.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, LOCK_NAME);
                    }
                }
            });
        } catch (Exception e) {
            log.error("{} 同步失败，已回滚该端：{}", tag, e.getMessage(), e);
            return null;
        }
    }

    private Resource resolve(String location) {
        return location == null || location.isBlank() ? null : resourceLoader.getResource(location.trim());
    }

    private void logPlan(String tag, MenuSyncPlan plan) {
        plan.getWarnings().forEach(w -> log.warn("{} {}", tag, w));
        plan.getBackfills().forEach(b -> log.info("{} 补 route_key：{}（{}）→ {}", tag, b.id(), b.menuName(), b.routeKey()));
        plan.getInserts().forEach(i -> log.info("{} 新增：{} {}（{}）", tag, i.getId(), i.getRouteKey(), i.getMenuName()));
        plan.getUpdates().forEach(u -> log.info("{} 更新：{} {} {}{}{}", tag, u.id(), u.routeKey(),
                u.changeLog(), u.claimed() ? " [认领手工菜单]" : "", u.reenabled() ? " [重新启用]" : ""));
        plan.getDisables().forEach(d -> log.info("{} 停用：{} {}（{}）", tag, d.id(), d.routeKey(), d.menuName()));
        plan.getDisablesSkipped().forEach(d -> log.info("{} 清单里已没有、但本次不停用：{} {}（{}）",
                tag, d.id(), d.routeKey(), d.menuName()));
    }

    void execute(MenuSyncPlan plan, Date now) {
        for (MenuSyncPlan.Backfill b : plan.getBackfills()) {
            jdbc.update("UPDATE sys_menus SET route_key = ? WHERE id = ? AND route_key IS NULL", b.routeKey(), b.id());
        }
        for (SysMenus m : plan.getInserts()) {
            jdbc.update("INSERT INTO sys_menus (id, menu_name, menu_url, parent_id, sort_order, icon, visible, menu_type, "
                            + "permission, component, route_name, keep_alive, always_show, redirect, client, route_key, managed, "
                            + "disabled, create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    m.getId(), m.getMenuName(), m.getMenuUrl(), m.getParentId(), m.getSortOrder(), m.getIcon(),
                    m.getVisible(), m.getMenuType(), m.getPermission(), m.getComponent(), m.getRouteName(),
                    m.getKeepAlive(), m.getAlwaysShow(), m.getRedirect(), m.getClient(), m.getRouteKey(),
                    m.getManaged(), m.getDisabled(), m.getCreateTime(), m.getUpdateTime());
        }
        for (MenuSyncPlan.Update u : plan.getUpdates()) {
            StringJoiner sets = new StringJoiner(", ");
            List<Object> args = new ArrayList<>();
            for (Map.Entry<String, Object> c : u.changes().entrySet()) {
                // 列名来自计划器里的固定集合，不是外部输入
                sets.add(c.getKey() + " = ?");
                args.add(c.getValue());
            }
            sets.add("update_time = ?");
            args.add(now);
            args.add(u.id());
            jdbc.update("UPDATE sys_menus SET " + sets + " WHERE id = ?", args.toArray());
        }
        for (MenuSyncPlan.Disable d : plan.getDisables()) {
            jdbc.update("UPDATE sys_menus SET disabled = 1, update_time = ? WHERE id = ? AND managed = 1", now, d.id());
        }
    }

    /**
     * 一次性授权：把用户端菜单 appRoute 授予“已有 sourceMenuId 的角色”（excluded 除外），完成后写 sys_setup_marker。
     * 目标菜单不存在（用户端清单还没有这一页）时什么都不做、不写标记，等以后清单加上再执行。
     */
    void grantOnce(String marker, String appRoute, String sourceMenuId, Set<String> excluded, boolean apply) {
        grantOnce(marker, appRoute, sourceMenuId, excluded, apply, Set.of());
    }

    /** @param plannedAppKeys dry-run 时本次计划新增的用户端 route_key（小写），用于预览“新增后会授予谁” */
    void grantOnce(String marker, String appRoute, String sourceMenuId, Set<String> excluded, boolean apply,
                   Set<String> plannedAppKeys) {
        String route = MenuPaths.normalize(appRoute);
        if (route == null) {
            return;
        }
        String tag = "菜单首次授权[" + marker + "]" + (apply ? "" : "[dry-run]");
        try {
            tx.executeWithoutResult(status -> {
                Integer done = jdbc.queryForObject("SELECT COUNT(*) FROM sys_setup_marker WHERE marker_key = ?",
                        Integer.class, marker);
                if (done != null && done > 0) {
                    return;
                }
                List<String> targets = jdbc.queryForList(
                        "SELECT id FROM sys_menus WHERE client = ? AND disabled = 0 AND LOWER(route_key) = LOWER(?) ORDER BY id",
                        String.class, MenuClients.APP, route);
                boolean plannedOnly = targets.isEmpty() && !apply && plannedAppKeys.contains(MenuPaths.key(route));
                if (targets.isEmpty() && !plannedOnly) {
                    log.info("{} 用户端菜单 {} 还不存在，暂不执行（清单加上这一页后的下次启动会执行）", tag, route);
                    return;
                }
                String target = plannedOnly ? "（待新增）" : targets.get(0);
                Set<String> excludedLower = new HashSet<>();
                excluded.forEach(r -> excludedLower.add(r.toLowerCase(Locale.ROOT)));
                Set<String> roles = new LinkedHashSet<>();
                for (String r : jdbc.queryForList(
                        "SELECT DISTINCT rold_id FROM sys_role_menu WHERE menu_id = ? AND rold_id IS NOT NULL ORDER BY rold_id",
                        String.class, sourceMenuId)) {
                    String role = r.trim();
                    if (!role.isEmpty() && !excludedLower.contains(role.toLowerCase(Locale.ROOT))) {
                        roles.add(role);
                    }
                }
                if (!plannedOnly) {
                    roles.removeAll(new HashSet<>(jdbc.queryForList(
                            "SELECT rold_id FROM sys_role_menu WHERE menu_id = ?", String.class, target)));
                }
                if (!apply) {
                    log.info("{} 计划把 {}（{}）授予已有 {} 的角色：{}", tag, target, route, sourceMenuId, roles);
                    return;
                }
                for (String role : roles) {
                    jdbc.update("INSERT INTO sys_role_menu (id, rold_id, menu_id) VALUES (?, ?, ?)",
                            UUID.randomUUID().toString().replace("-", ""), role, target);
                }
                String note = "granted " + target + " to " + roles;
                jdbc.update("INSERT INTO sys_setup_marker (marker_key, done_at, note) VALUES (?, ?, ?)",
                        marker, new Date(), note.length() > 500 ? note.substring(0, 500) : note);
                log.info("{} 已把 {}（{}）授予 {} 个角色：{}", tag, target, route, roles.size(), roles);
            });
        } catch (Exception e) {
            log.error("{} 执行失败，已回滚：{}", tag, e.getMessage(), e);
        }
    }
}
