package springboot.service.menu;

import java.util.Locale;

/**
 * 菜单路径规范化：trim、补前导 /、合并连续 /、去掉末尾 /，保留大小写；比较时用 {@link #key} 忽略大小写。
 */
public final class MenuPaths {

    private MenuPaths() {
    }

    public static boolean isExternal(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim().toLowerCase(Locale.ROOT);
        return t.startsWith("http://") || t.startsWith("https://");
    }

    /** @return 规范化后的路径；空串返回 null；外链原样返回（仅 trim） */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (isExternal(s)) {
            return s;
        }
        s = s.replaceAll("/{2,}", "/");
        if (!s.startsWith("/")) {
            s = "/" + s;
        }
        while (s.length() > 1 && s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** 比较用的键（忽略大小写） */
    public static String key(String normalized) {
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    /** 父路径 + 相对段 */
    public static String join(String parentPath, String segment) {
        if (segment == null || segment.isBlank()) {
            return parentPath;
        }
        if (parentPath == null || parentPath.isBlank() || "/".equals(parentPath)) {
            return normalize(segment);
        }
        return normalize(parentPath + "/" + segment.trim());
    }

    /**
     * 写入 menu_url 的值：顶级写完整路径；子级写相对父级的段；子路径不以父路径为前缀时写完整路径。
     */
    public static String menuUrl(String path, String parentPath) {
        if (parentPath == null || isExternal(path)) {
            return path;
        }
        String prefix = key(parentPath) + "/";
        if (key(path).startsWith(prefix) && path.length() > prefix.length()) {
            return path.substring(prefix.length());
        }
        return path;
    }

    /** 由路径推导路由名：/tool/wallpaper → ToolWallpaper，/crab/:id → CrabId */
    public static String routeName(String path) {
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("[^A-Za-z0-9]+")) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.length() == 0 ? "Root" : sb.toString();
    }
}
