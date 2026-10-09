package springboot.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminAccessRulesTest {

    private static boolean allowed(String method, String path, List<String> menus, Set<String> components) {
        return AdminAccessRules.match(method, path).allows(menus, components);
    }

    private static boolean allowed(String method, String path, String... menus) {
        return allowed(method, path, List.of(menus), Set.of());
    }

    @Test
    void userAndRolePages() {
        assertTrue(allowed("GET", "/admin/sysUsers/getUsers", "menu_user"));
        assertTrue(allowed("GET", "/admin/sysUsers/getUsers", "menu_regcode_user"), "注册码客户页要查主用户");
        assertFalse(allowed("POST", "/admin/sysUsers/userSaveOrUpdate", "menu_regcode_user"));
        assertTrue(allowed("POST", "/admin/sysUsers/delUsers", "menu_user"));

        assertTrue(allowed("GET", "/admin/sysRoles/getAll", "menu_user"), "用户页要选角色");
        assertFalse(allowed("POST", "/admin/sysRoles/add", "menu_user"));
        assertTrue(allowed("POST", "/admin/sysRoles/add", "menu_role"));
        assertTrue(allowed("GET", "/admin/sysRoles/menus", "menu_role"));
        assertFalse(allowed("POST", "/admin/sysRoles/menus", "menu_role"), "给角色分配菜单只允许 ROOT");

        assertTrue(allowed("GET", "/admin/sysMenus/list", "menu_role"));
        assertTrue(allowed("GET", "/admin/sysMenus/list", "menu_menu"));
        assertFalse(allowed("POST", "/admin/sysMenus/add", "menu_menu"), "路由增删改只允许 ROOT");
    }

    @Test
    void otherModules() {
        assertTrue(allowed("GET", "/admin/sysOperationLog/getAll", "menu_oplog"));
        assertTrue(allowed("GET", "/admin/systemData/status"), "任何管理端账号都能看系统状态");
        assertFalse(allowed("GET", "/admin/systemData/export", "menu_user", "menu_oplog"));
        assertTrue(allowed("DELETE", "/admin/sysTasks/del", "menu_tasks"));
        assertTrue(allowed("GET", "/admin/comRegistration/getAll", "menu_registration"));
        assertTrue(allowed("GET", "/admin/regCodeConfig/list", "menu_regcode_user"));
        assertFalse(allowed("POST", "/admin/regCodeConfig/add", "menu_regcode_user"));
        assertTrue(allowed("POST", "/admin/regCodeConfig/add", "menu_regcode_config"));
        assertTrue(allowed("POST", "/admin/regCodeUser/save", "menu_regcode_user"));
        assertTrue(allowed("GET", "/admin/crabShipment/getAll", "menu_crab"));
        assertFalse(allowed("GET", "/admin/crabShipment/getAll", "menu_mindmap"));
        assertTrue(allowed("GET", "/admin/badmintonBill/getAll", "menu_badminton"));
        assertFalse(allowed("GET", "/admin/badmintonBill/getAll", "menu_crab"));
        assertTrue(allowed("POST", "/admin/mindmap/save", "menu_mindmap"));
    }

    @Test
    void wallpaperByComponent() {
        assertTrue(allowed("GET", "/admin/wallpaper/group/list", List.of("m_x"), Set.of("tool/wallpaper/index")));
        assertFalse(allowed("GET", "/admin/wallpaper/group/list", List.of("menu_mindmap"), Set.of("tool/mindmap/index")));
    }

    @Test
    void notesByComponent() {
        assertTrue(allowed("POST", "/admin/notes/draft/upload", List.of("m_notes"), Set.of("tool/notes/index")));
        assertTrue(allowed("GET", "/admin/notes/docs", List.of(), Set.of(AdminAccessRules.NOTES_COMPONENT)));
        assertFalse(allowed("GET", "/admin/notes/source", List.of("menu_mindmap"), Set.of("tool/mindmap/index")));
    }

    @Test
    void unknownAdminPathIsRootOnly() {
        assertSame(AdminAccessRules.DEFAULT_ROOT_ONLY, AdminAccessRules.match("GET", "/admin/newModule/list"));
        assertFalse(allowed("GET", "/admin/newModule/list", "menu_user", "menu_role", "menu_menu"));
    }

    @Test
    void normalizeAndSuspicious() {
        assertEquals("/admin/sysUsers/getUsers", AdminAccessRules.normalize("/admin//sysUsers/getUsers/", null, null, null));
        assertEquals("/admin/a/b", AdminAccessRules.normalize("", null, "/ctx/admin/a/b", "/ctx"));
        assertEquals("/admin/a/b", AdminAccessRules.normalize("/admin", "/a/b", "/admin/a/b", ""));
        assertTrue(AdminAccessRules.isSuspicious("/admin/sysUsers/../sysMenus/add"));
        assertTrue(AdminAccessRules.isSuspicious("/admin/sysMenus/add;jsessionid=1"));
        assertTrue(AdminAccessRules.isSuspicious("/admin//sysMenus/add"));
        assertTrue(AdminAccessRules.isSuspicious("/admin/sysUsers%2F..%2FsysMenus/add"));
        assertTrue(AdminAccessRules.isSuspicious("/admin/sysUsers/%2e%2e/sysMenus/add"));
        assertFalse(AdminAccessRules.isSuspicious("/admin/sysUsers/getUsers"));
    }
}
