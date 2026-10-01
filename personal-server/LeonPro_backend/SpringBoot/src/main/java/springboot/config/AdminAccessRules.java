package springboot.config;

import org.springframework.util.AntPathMatcher;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * /admin/** 按角色菜单授权的规则表（ROOT 之外的角色才会查这张表）。
 * <p>
 * 按顺序取第一条匹配的规则；一条都没匹配到的 /admin 路径只允许 ROOT（新接口默认关闭，需在这里显式登记）。
 * 菜单以 sys_role_menu 里的菜单 id 为准；壁纸没有固定菜单 id，按菜单的 component 判断。
 */
public final class AdminAccessRules {

    public enum Kind {
        /** 只有 ROOT */
        ROOT_ONLY,
        /** 任何能进管理端的账号 */
        ANY_ADMIN,
        /** 角色拥有 menuIds 中任意一个 */
        MENU,
        /** 角色拥有的菜单里有 component 等于 component 的 */
        COMPONENT
    }

    public record Rule(String method, String pattern, Kind kind, Set<String> menuIds, String component) {

        boolean matches(String reqMethod, String path) {
            if (method != null && !method.equalsIgnoreCase(reqMethod)) {
                return false;
            }
            return MATCHER.match(pattern, path);
        }

        /** 非 ROOT 用户是否满足本规则 */
        public boolean allows(Collection<String> ownedMenuIds, Collection<String> ownedComponents) {
            return switch (kind) {
                case ROOT_ONLY -> false;
                case ANY_ADMIN -> true;
                case MENU -> ownedMenuIds != null && ownedMenuIds.stream().anyMatch(menuIds::contains);
                case COMPONENT -> ownedComponents != null && ownedComponents.contains(component);
            };
        }
    }

    public static final String WALLPAPER_COMPONENT = "tool/wallpaper/index";

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /** 未登记路径的兜底：只允许 ROOT */
    public static final Rule DEFAULT_ROOT_ONLY = new Rule(null, "/admin/**", Kind.ROOT_ONLY, Set.of(), null);

    private static final List<Rule> RULES = List.of(
            menu(null, "/admin/sysUsers/getUsers", "menu_user", "menu_regcode_user"),
            menu(null, "/admin/sysUsers/**", "menu_user"),
            menu(null, "/admin/sysRoles/getAll", "menu_role", "menu_user"),
            rootOnly("POST", "/admin/sysRoles/menus"),
            menu(null, "/admin/sysRoles/**", "menu_role"),
            menu(null, "/admin/sysMenus/list", "menu_menu", "menu_role"),
            rootOnly(null, "/admin/sysMenus/**"),
            menu(null, "/admin/sysOperationLog/**", "menu_oplog"),
            new Rule(null, "/admin/systemData/status", Kind.ANY_ADMIN, Set.of(), null),
            rootOnly(null, "/admin/systemData/**"),
            menu(null, "/admin/sysTasks/**", "menu_tasks"),
            menu(null, "/admin/comRegistration/**", "menu_registration"),
            menu(null, "/admin/regCodeConfig/list", "menu_regcode_config", "menu_regcode_user"),
            menu(null, "/admin/regCodeConfig/**", "menu_regcode_config"),
            menu(null, "/admin/regCodeUser/**", "menu_regcode_user"),
            menu(null, "/admin/crabShipment/**", "menu_crab"),
            menu(null, "/admin/badmintonBill/**", "menu_badminton"),
            menu(null, "/admin/mindmap/**", "menu_mindmap"),
            new Rule(null, "/admin/wallpaper/**", Kind.COMPONENT, Set.of(), WALLPAPER_COMPONENT)
    );

    private AdminAccessRules() {
    }

    private static Rule menu(String method, String pattern, String... menuIds) {
        return new Rule(method, pattern, Kind.MENU, Set.copyOf(Arrays.asList(menuIds)), null);
    }

    private static Rule rootOnly(String method, String pattern) {
        return new Rule(method, pattern, Kind.ROOT_ONLY, Set.of(), null);
    }

    public static List<Rule> rules() {
        return RULES;
    }

    /** 第一条匹配的规则；没有匹配时返回 {@link #DEFAULT_ROOT_ONLY} */
    public static Rule match(String method, String path) {
        for (Rule r : RULES) {
            if (r.matches(method, path)) {
                return r;
            }
        }
        return DEFAULT_ROOT_ONLY;
    }

    /**
     * 原始 URI 里带这些片段直接拒绝，避免 /admin/x/../y、矩阵参数、编码斜杠等绕过规则匹配。
     */
    public static boolean isSuspicious(String rawUri) {
        if (rawUri == null) {
            return true;
        }
        String u = rawUri.toLowerCase(Locale.ROOT);
        return u.contains("..") || u.contains(";") || u.contains("//") || u.contains("%2") || u.contains("\\")
                || u.contains("%5c");
    }

    /** 统一路径：servletPath + pathInfo（servletPath 为空时用 requestURI 去掉 contextPath），并合并多余斜杠 */
    public static String normalize(String servletPath, String pathInfo, String requestUri, String contextPath) {
        String path;
        if (servletPath != null && !servletPath.isEmpty()) {
            path = servletPath + (pathInfo == null ? "" : pathInfo);
        } else {
            path = requestUri == null ? "" : requestUri;
            if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
                path = path.substring(contextPath.length());
            }
        }
        path = path.replaceAll("/{2,}", "/");
        if (path.isEmpty()) {
            return "/";
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
