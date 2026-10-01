package springboot.service.menu;

import springboot.domain.SysMenus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 同步计划（纯计算，不访问数据库）：
 * <ol>
 *   <li>给本端 route_key 为空的存量行按 parent_id 拼出完整路径并回填（重复的留空并告警）；</li>
 *   <li>按父级优先遍历清单：按 (client, route_key) 找到就只更新有变化的字段并设 managed=1、disabled=0，找不到就插入；</li>
 *   <li>本端 managed=1 且不在清单里的行标记 disabled=1（可关闭；一次要停用超过一半时拒绝）。</li>
 * </ol>
 * 已有行永远保留原 id；手工菜单（managed=0 且对不上清单）除回填 route_key 外不碰。
 */
public final class MenuSyncPlanner {

    /** 保护阈值：本端有效 managed 行至少这么多时，一次停用超过一半就拒绝停用 */
    static final int GUARD_MIN_ACTIVE = 4;

    private MenuSyncPlanner() {
    }

    public record Options(boolean disableMissing, boolean disableGuard) {
    }

    public static MenuSyncPlan plan(String client, List<MenuManifestEntry> entries, List<SysMenus> clientRows,
                                    Set<String> allIds, Options options, Date now) {
        MenuSyncPlan plan = new MenuSyncPlan(client, entries.size());
        List<SysMenus> rows = clientRows == null ? new ArrayList<>() : clientRows;
        Set<String> takenIds = new HashSet<>();
        if (allIds != null) {
            allIds.stream().filter(Objects::nonNull).map(s -> s.toLowerCase(Locale.ROOT)).forEach(takenIds::add);
        }
        rows.stream().map(SysMenus::getId).filter(Objects::nonNull).map(s -> s.toLowerCase(Locale.ROOT)).forEach(takenIds::add);

        backfillRouteKeys(rows, plan);

        Map<String, SysMenus> byKey = new HashMap<>();
        for (SysMenus r : rows) {
            if (r.getRouteKey() == null) {
                continue;
            }
            if (byKey.putIfAbsent(MenuPaths.key(r.getRouteKey()), r) != null) {
                plan.getWarnings().add("route_key 重复（" + r.getRouteKey() + "），行 " + r.getId() + " 不参与匹配");
            }
        }

        Map<String, MenuManifestEntry> manifestByKey = new LinkedHashMap<>();
        entries.forEach(e -> manifestByKey.put(MenuPaths.key(e.getPath()), e));
        Map<String, String> resolvedIds = new HashMap<>();
        Set<String> matchedRowIds = new HashSet<>();

        for (MenuManifestEntry e : parentsFirst(entries, manifestByKey)) {
            String key = MenuPaths.key(e.getPath());
            MenuManifestEntry parent = e.getParent() == null ? null : manifestByKey.get(MenuPaths.key(e.getParent()));
            String parentId = parent == null ? "0" : resolvedIds.get(MenuPaths.key(parent.getPath()));
            Map<String, Object> want = desired(e, parent, parentId);
            SysMenus row = byKey.get(key);
            if (row == null) {
                SysMenus insert = newRow(client, e, want, entries, takenIds, now);
                takenIds.add(insert.getId().toLowerCase(Locale.ROOT));
                plan.getInserts().add(insert);
                resolvedIds.put(key, insert.getId());
                continue;
            }
            matchedRowIds.add(row.getId());
            resolvedIds.put(key, row.getId());
            Map<String, Object> changes = new LinkedHashMap<>();
            List<String> changeLog = new ArrayList<>();
            for (Map.Entry<String, Object> w : want.entrySet()) {
                Object cur = current(row, w.getKey());
                if (!sameValue(w.getKey(), cur, w.getValue())) {
                    changes.put(w.getKey(), w.getValue());
                    changeLog.add(w.getKey() + ": " + cur + " → " + w.getValue());
                }
            }
            if (!e.getPath().equals(row.getRouteKey())) {
                changes.put("route_key", e.getPath());
                changeLog.add("route_key: " + row.getRouteKey() + " → " + e.getPath());
            }
            boolean claimed = !Integer.valueOf(1).equals(row.getManaged());
            boolean reenabled = row.getDisabled() != null && row.getDisabled() != 0;
            if (claimed) {
                changes.put("managed", 1);
                changeLog.add("managed: " + row.getManaged() + " → 1");
            }
            if (reenabled) {
                changes.put("disabled", 0);
                changeLog.add("disabled: " + row.getDisabled() + " → 0");
            }
            if (changes.isEmpty()) {
                plan.incUnchanged();
            } else {
                plan.getUpdates().add(new MenuSyncPlan.Update(row.getId(), e.getPath(), changes, changeLog, claimed, reenabled));
            }
        }

        List<MenuSyncPlan.Disable> missing = new ArrayList<>();
        int activeManaged = 0;
        int manual = 0;
        for (SysMenus r : rows) {
            boolean managed = Integer.valueOf(1).equals(r.getManaged());
            boolean disabled = r.getDisabled() != null && r.getDisabled() != 0;
            if (!managed) {
                if (!matchedRowIds.contains(r.getId())) {
                    manual++;
                }
                continue;
            }
            if (disabled) {
                continue;
            }
            activeManaged++;
            if (r.getRouteKey() != null && !manifestByKey.containsKey(MenuPaths.key(r.getRouteKey()))) {
                missing.add(new MenuSyncPlan.Disable(r.getId(), r.getRouteKey(), r.getMenuName()));
            }
        }
        plan.setManualUntouched(manual);
        if (!missing.isEmpty()) {
            if (!options.disableMissing()) {
                plan.getDisablesSkipped().addAll(missing);
            } else if (options.disableGuard() && activeManaged >= GUARD_MIN_ACTIVE && missing.size() * 2 > activeManaged) {
                plan.setDisableBlockedByGuard(true);
                plan.getDisablesSkipped().addAll(missing);
                plan.getWarnings().add("本次要停用 " + missing.size() + " 条，超过现有 " + activeManaged
                        + " 条清单菜单的一半，已拒绝停用（请检查清单是否完整；确认无误可临时设置 app.menu-sync.disable-guard=false）");
            } else {
                plan.getDisables().addAll(missing);
            }
        }
        return plan;
    }

    /** 回填本端 route_key 为空的行（按钮除外）。外链原样作为 key；父级不存在的相对路径跳过。 */
    static void backfillRouteKeys(List<SysMenus> rows, MenuSyncPlan plan) {
        Map<String, SysMenus> byId = new HashMap<>();
        Set<String> keys = new HashSet<>();
        for (SysMenus r : rows) {
            if (r.getId() != null) {
                byId.put(r.getId(), r);
            }
            if (r.getRouteKey() != null) {
                keys.add(MenuPaths.key(r.getRouteKey()));
            }
        }
        List<SysMenus> candidates = rows.stream()
                .filter(r -> r.getRouteKey() == null && !Integer.valueOf(2).equals(r.getMenuType()))
                .sorted(Comparator.comparing((SysMenus r) -> r.getSortOrder() == null ? Integer.MAX_VALUE : r.getSortOrder())
                        .thenComparing(r -> r.getId() == null ? "" : r.getId()))
                .toList();
        Map<String, String> computed = new HashMap<>();
        for (SysMenus r : candidates) {
            String path = fullPath(r, byId, computed, 0);
            if (path == null) {
                if (r.getMenuUrl() != null && !r.getMenuUrl().isBlank()) {
                    plan.getWarnings().add("菜单 " + r.getId() + "（" + r.getMenuName() + "）拼不出完整路径（父级不存在或成环），未回填 route_key");
                }
                continue;
            }
            if (path.length() > MenuManifestLoader.MAX_PATH_LENGTH) {
                plan.getWarnings().add("菜单 " + r.getId() + "（" + r.getMenuName() + "）的完整路径超过 "
                        + MenuManifestLoader.MAX_PATH_LENGTH + " 个字符，未回填 route_key");
                continue;
            }
            if (!keys.add(MenuPaths.key(path))) {
                plan.getWarnings().add("菜单 " + r.getId() + "（" + r.getMenuName() + "）的完整路径 " + path
                        + " 与已有菜单重复，route_key 留空（按手工菜单保留）");
                continue;
            }
            r.setRouteKey(path);
            plan.getBackfills().add(new MenuSyncPlan.Backfill(r.getId(), path, r.getMenuName()));
        }
    }

    private static String fullPath(SysMenus r, Map<String, SysMenus> byId, Map<String, String> memo, int depth) {
        if (r.getRouteKey() != null) {
            return r.getRouteKey();
        }
        if (depth > 20) {
            return null;
        }
        if (memo.containsKey(r.getId())) {
            return memo.get(r.getId());
        }
        String url = r.getMenuUrl() == null ? "" : r.getMenuUrl().trim();
        String result;
        if (url.isEmpty()) {
            result = null;
        } else if (MenuPaths.isExternal(url) || url.startsWith("/") || isTop(r.getParentId())) {
            result = MenuPaths.normalize(url);
        } else {
            SysMenus parent = byId.get(r.getParentId().trim());
            String parentPath = parent == null ? null : fullPath(parent, byId, memo, depth + 1);
            result = parentPath == null || MenuPaths.isExternal(parentPath) ? null : MenuPaths.join(parentPath, url);
        }
        if (r.getId() != null) {
            memo.put(r.getId(), result);
        }
        return result;
    }

    private static boolean isTop(String parentId) {
        return parentId == null || parentId.isBlank() || "0".equals(parentId.trim());
    }

    private static List<MenuManifestEntry> parentsFirst(List<MenuManifestEntry> entries, Map<String, MenuManifestEntry> byKey) {
        Map<MenuManifestEntry, Integer> depth = new HashMap<>();
        for (MenuManifestEntry e : entries) {
            int d = 0;
            MenuManifestEntry cur = e;
            while (cur.getParent() != null && d < 50) {
                cur = byKey.get(MenuPaths.key(cur.getParent()));
                if (cur == null) {
                    break;
                }
                d++;
            }
            depth.put(e, d);
        }
        List<MenuManifestEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt(depth::get));
        return sorted;
    }

    /** 清单负责的字段。icon / redirect / routeName / alwaysShow 只在清单里写了才管，没写就保留库里的值 */
    static Map<String, Object> desired(MenuManifestEntry e, MenuManifestEntry parent, String parentId) {
        Map<String, Object> want = new LinkedHashMap<>();
        want.put("menu_name", e.getName().trim());
        want.put("menu_url", MenuPaths.menuUrl(e.getPath(), parent == null ? null : parent.getPath()));
        want.put("parent_id", parentId);
        want.put("sort_order", e.getSort() == null ? 0 : e.getSort());
        want.put("menu_type", e.isDir() ? 0 : 1);
        want.put("component", e.isDir() ? "Layout" : e.getComponent().trim());
        want.put("visible", e.isHidden() ? 0 : 1);
        want.put("keep_alive", Boolean.TRUE.equals(e.getKeepAlive()) ? 1 : 0);
        if (e.getIcon() != null) {
            want.put("icon", e.getIcon().trim());
        }
        if (e.getRedirect() != null) {
            want.put("redirect", e.getRedirect().trim());
        }
        if (e.getRouteName() != null) {
            want.put("route_name", e.getRouteName().trim());
        }
        if (e.getAlwaysShow() != null) {
            want.put("always_show", e.getAlwaysShow() ? 1 : 0);
        }
        return want;
    }

    private static SysMenus newRow(String client, MenuManifestEntry e, Map<String, Object> want,
                                   List<MenuManifestEntry> entries, Set<String> takenIds, Date now) {
        SysMenus m = new SysMenus();
        m.setId(deriveId(client, e.getPath(), takenIds));
        m.setMenuName((String) want.get("menu_name"));
        m.setMenuUrl((String) want.get("menu_url"));
        m.setParentId((String) want.get("parent_id"));
        m.setSortOrder((Integer) want.get("sort_order"));
        m.setMenuType((Integer) want.get("menu_type"));
        m.setComponent((String) want.get("component"));
        m.setVisible((Integer) want.get("visible"));
        m.setKeepAlive((Integer) want.get("keep_alive"));
        m.setIcon((String) want.get("icon"));
        m.setRedirect(want.containsKey("redirect") ? (String) want.get("redirect") : defaultRedirect(e, entries));
        m.setRouteName(want.containsKey("route_name") ? (String) want.get("route_name") : MenuPaths.routeName(e.getPath()));
        m.setAlwaysShow(want.containsKey("always_show") ? (Integer) want.get("always_show") : 0);
        m.setClient(client);
        m.setRouteKey(e.getPath());
        m.setManaged(1);
        m.setDisabled(0);
        m.setCreateTime(now);
        m.setUpdateTime(now);
        return m;
    }

    /** 目录默认跳到第一个可见子页 */
    private static String defaultRedirect(MenuManifestEntry e, List<MenuManifestEntry> entries) {
        if (!e.isDir()) {
            return null;
        }
        String key = MenuPaths.key(e.getPath());
        return entries.stream()
                .filter(c -> c.getParent() != null && MenuPaths.key(c.getParent()).equals(key) && !c.isHidden())
                .min(Comparator.comparingInt(c -> c.getSort() == null ? 0 : c.getSort()))
                .map(MenuManifestEntry::getPath)
                .orElse(null);
    }

    /**
     * 新行 id：先查历史 id 表；否则 menu_[app_]路径转写（非字母数字转 _）；超过 64 位或已被占用时用 menu_ + sha1 前 24 位。
     */
    static String deriveId(String client, String path, Set<String> takenIds) {
        String key = MenuPaths.key(path);
        String legacy = LegacyMenuIds.of(client, key);
        if (legacy != null && !takenIds.contains(legacy)) {
            return legacy;
        }
        String slug = key.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        String id = "menu_" + (MenuClients.APP.equals(client) ? "app_" : "") + (slug.isEmpty() ? "root" : slug);
        if (id.length() <= 64 && !takenIds.contains(id)) {
            return id;
        }
        String hashed = "menu_" + sha1(client + ":" + key).substring(0, 24);
        if (!takenIds.contains(hashed)) {
            return hashed;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String sha1(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Object current(SysMenus r, String column) {
        return switch (column) {
            case "menu_name" -> r.getMenuName();
            case "menu_url" -> r.getMenuUrl();
            case "parent_id" -> r.getParentId();
            case "sort_order" -> r.getSortOrder();
            case "menu_type" -> r.getMenuType();
            case "component" -> r.getComponent();
            case "visible" -> r.getVisible();
            case "keep_alive" -> r.getKeepAlive();
            case "icon" -> r.getIcon();
            case "redirect" -> r.getRedirect();
            case "route_name" -> r.getRouteName();
            case "always_show" -> r.getAlwaysShow();
            default -> throw new IllegalArgumentException(column);
        };
    }

    private static boolean sameValue(String column, Object cur, Object want) {
        if ("parent_id".equals(column) && "0".equals(want)) {
            return cur == null || String.valueOf(cur).isBlank() || "0".equals(String.valueOf(cur).trim());
        }
        return Objects.equals(cur, want);
    }
}
