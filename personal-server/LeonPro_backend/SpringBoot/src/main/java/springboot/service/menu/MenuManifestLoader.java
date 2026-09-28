package springboot.service.menu;

import org.springframework.core.io.Resource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 读取并校验 menus.json。校验规则与前端 scripts/check-menus.mjs 一致（组件文件是否存在除外，后端无法判断）：
 * path 以 / 开头且唯一（忽略大小写）、name 必填、type 只能是 dir/page、client 与文件所属端一致、
 * page 必须有 component、dir 的 component 为空或 Layout、parent 必须指向本文件里的条目且不能成环。
 * 校验通过时 path / parent 会被规范化。
 */
public final class MenuManifestLoader {

    /** 与 sys_menus.route_key 列长度一致 */
    public static final int MAX_PATH_LENGTH = 191;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<MenuManifestEntry>> LIST_TYPE = new TypeReference<>() {
    };

    private MenuManifestLoader() {
    }

    public static MenuManifest load(String client, Resource resource) {
        String location = resource == null ? "(null)" : resource.getDescription();
        if (resource == null || !resource.exists()) {
            return new MenuManifest(client, MenuManifest.Status.MISSING, location, List.of(), List.of(), List.of());
        }
        try (InputStream in = resource.getInputStream()) {
            return parse(client, location, in.readAllBytes());
        } catch (Exception e) {
            return invalid(client, location, "读取失败：" + e.getMessage());
        }
    }

    public static MenuManifest parse(String client, String location, byte[] json) {
        List<MenuManifestEntry> entries;
        try {
            entries = MAPPER.readValue(json, LIST_TYPE);
        } catch (Exception e) {
            return invalid(client, location, "JSON 解析失败（根节点必须是数组，字段类型要正确）：" + e.getMessage());
        }
        if (entries == null) {
            return invalid(client, location, "清单内容为 null");
        }
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        validate(client, entries, errors, warnings);
        if (!errors.isEmpty()) {
            return new MenuManifest(client, MenuManifest.Status.INVALID, location, List.of(), errors, warnings);
        }
        return new MenuManifest(client, MenuManifest.Status.LOADED, location, entries, List.of(), warnings);
    }

    static void validate(String client, List<MenuManifestEntry> entries, List<String> errors, List<String> warnings) {
        Map<String, MenuManifestEntry> byKey = new HashMap<>();
        Set<String> routeNames = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            MenuManifestEntry e = entries.get(i);
            String at = "第 " + (i + 1) + " 项";
            if (e == null) {
                errors.add(at + "：为空");
                continue;
            }
            if (e.getPath() == null || !e.getPath().trim().startsWith("/")) {
                errors.add(at + "：path 必须以 / 开头（" + e.getPath() + "）");
                continue;
            }
            if (MenuPaths.isExternal(e.getPath())) {
                errors.add(at + "：path 不能是外链（" + e.getPath() + "）");
                continue;
            }
            e.setPath(MenuPaths.normalize(e.getPath()));
            at = at + "（" + e.getPath() + "）";
            if (e.getPath().length() > MAX_PATH_LENGTH) {
                errors.add(at + "：path 不能超过 " + MAX_PATH_LENGTH + " 个字符");
            }
            if (byKey.putIfAbsent(MenuPaths.key(e.getPath()), e) != null) {
                errors.add(at + "：path 重复");
            }
            if (e.getName() == null || e.getName().isBlank()) {
                errors.add(at + "：name 必填");
            }
            if (!MenuManifestEntry.TYPE_DIR.equals(e.getType()) && !MenuManifestEntry.TYPE_PAGE.equals(e.getType())) {
                errors.add(at + "：type 只能是 dir 或 page");
            }
            if (!client.equals(e.getClient())) {
                errors.add(at + "：client 必须是 " + client + "（实际 " + e.getClient() + "）");
            }
            String component = e.getComponent() == null ? "" : e.getComponent().trim();
            if (MenuManifestEntry.TYPE_PAGE.equals(e.getType()) && (component.isEmpty() || "Layout".equals(component))) {
                errors.add(at + "：page 必须填写 component");
            }
            if (MenuManifestEntry.TYPE_DIR.equals(e.getType()) && !component.isEmpty() && !"Layout".equals(component)) {
                errors.add(at + "：dir 的 component 只能为空或 Layout");
            }
            if (e.getParent() != null && e.getParent().isBlank()) {
                e.setParent(null);
            }
            if (e.getParent() != null) {
                e.setParent(MenuPaths.normalize(e.getParent()));
            }
            if (e.getRouteName() != null && !e.getRouteName().isBlank() && !routeNames.add(e.getRouteName().trim())) {
                warnings.add(at + "：routeName 重复（" + e.getRouteName() + "）");
            }
        }
        if (!errors.isEmpty()) {
            return;
        }
        for (MenuManifestEntry e : entries) {
            if (e.getParent() == null) {
                continue;
            }
            if (MenuPaths.key(e.getParent()).equals(MenuPaths.key(e.getPath()))) {
                errors.add(e.getPath() + "：parent 不能是自己");
                continue;
            }
            if (!byKey.containsKey(MenuPaths.key(e.getParent()))) {
                errors.add(e.getPath() + "：parent " + e.getParent() + " 不在清单里");
                continue;
            }
            Set<String> seen = new HashSet<>();
            MenuManifestEntry cur = e;
            while (cur != null && cur.getParent() != null) {
                if (!seen.add(MenuPaths.key(cur.getPath()))) {
                    errors.add(e.getPath() + "：parent 成环");
                    break;
                }
                cur = byKey.get(MenuPaths.key(cur.getParent()));
            }
        }
    }

    private static MenuManifest invalid(String client, String location, String error) {
        return new MenuManifest(client, MenuManifest.Status.INVALID, location, List.of(), List.of(error), List.of());
    }
}
