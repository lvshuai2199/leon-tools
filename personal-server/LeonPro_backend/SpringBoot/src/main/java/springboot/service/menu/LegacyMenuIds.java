package springboot.service.menu;

import java.util.Map;

/**
 * 历史菜单 id：代码里有硬编码（菜单鉴权、注册码权限、种子授权），全新库按清单插入时也要得到同样的 id。
 * 已有库里匹配到的行永远保留原 id，这张表只影响“新插入”。
 */
public final class LegacyMenuIds {

    private static final Map<String, String> ADMIN = Map.ofEntries(
            Map.entry("/tool", "menu_tool"),
            Map.entry("/tool/trace", "menu_trace"),
            Map.entry("/tool/files", "menu_files"),
            Map.entry("/tool/documents", "menu_documents"),
            Map.entry("/tool/mindmap", "menu_mindmap"),
            Map.entry("/tool/wallpaper", "menu_wallpaper"),
            Map.entry("/regcode", "menu_regcode_center"),
            Map.entry("/regcode/generate", "menu_regcode"),
            Map.entry("/regcode/config", "menu_regcode_config"),
            Map.entry("/regcode/user", "menu_regcode_user"),
            Map.entry("/regcode/records", "menu_registration"),
            Map.entry("/work", "menu_work"),
            Map.entry("/work/tasks", "menu_tasks"),
            Map.entry("/work/crab", "menu_crab"),
            Map.entry("/system", "menu_system"),
            Map.entry("/system/user", "menu_user"),
            Map.entry("/system/role", "menu_role"),
            Map.entry("/system/menu", "menu_menu"),
            Map.entry("/system/oplog", "menu_oplog"));

    private LegacyMenuIds() {
    }

    /** @param key 已规范化且小写的路径 */
    public static String of(String client, String key) {
        return MenuClients.ADMIN.equals(client) ? ADMIN.get(key) : null;
    }
}
