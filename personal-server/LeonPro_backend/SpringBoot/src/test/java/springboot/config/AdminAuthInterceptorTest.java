package springboot.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import springboot.domain.SysMenus;
import springboot.domain.SysUsers;
import springboot.service.RegCodeAccessService;
import springboot.service.SysMenusService;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAuthInterceptorTest {

    RegCodeAccessService access;
    SysMenusService menus;
    AdminAuthInterceptor interceptor;
    SysUsers current;

    @BeforeEach
    void setUp() {
        access = mock(RegCodeAccessService.class);
        menus = mock(SysMenusService.class);
        interceptor = new AdminAuthInterceptor(access, menus, JsonMapper.builder().build());
        when(access.currentUser(any())).thenAnswer(inv -> current);
    }

    private static SysUsers user(String id, String roleId, String parentId) {
        SysUsers u = new SysUsers();
        u.setId(id);
        u.setRoleId(roleId);
        u.setParentId(parentId);
        return u;
    }

    private MockHttpServletResponse call(String method, String uri) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setServletPath(uri);
        MockHttpServletResponse res = new MockHttpServletResponse();
        boolean pass = interceptor.preHandle(req, res, new Object());
        if (pass) {
            res.setStatus(200);
        }
        return res;
    }

    @Test
    void webBlockedUsersAre403EvenWithMenus() throws Exception {
        current = user("sub", "role_admin", "p1");
        when(access.isWebBlocked(current)).thenReturn(true);
        when(access.menuIdsOf(current)).thenReturn(List.of("menu_user"));
        MockHttpServletResponse res = call("GET", "/admin/sysUsers/getUsers");
        assertEquals(403, res.getStatus());
        String body = res.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.contains("\"status\":403"), body);
        assertTrue(body.contains(AdminAuthInterceptor.WEB_BLOCKED_MESSAGE), body);
    }

    @Test
    void rootPassesEverything() throws Exception {
        current = user("root", "role_root", null);
        when(access.isRootUser(current)).thenReturn(true);
        assertEquals(200, call("POST", "/admin/sysMenus/add").getStatus());
        assertEquals(200, call("GET", "/admin/systemData/export").getStatus());
        assertEquals(200, call("GET", "/admin/whatever/new").getStatus());
    }

    @Test
    void roleMenusDecide() throws Exception {
        current = user("mgr", "role_admin", null);
        when(access.menuIdsOf(current)).thenReturn(List.of("menu_user"));
        assertEquals(200, call("GET", "/admin/sysUsers/getUsers").getStatus());
        assertEquals(200, call("GET", "/admin/sysRoles/getAll").getStatus());
        assertEquals(403, call("POST", "/admin/sysRoles/add").getStatus());
        assertEquals(403, call("GET", "/admin/crabShipment/getAll").getStatus());
        assertEquals(403, call("GET", "/admin/unknown/list").getStatus());
        assertEquals(200, call("GET", "/admin/systemData/status").getStatus());
        assertEquals(403, call("GET", "/admin/systemData/export").getStatus());
    }

    @Test
    void traversalIsRejected() throws Exception {
        current = user("mgr", "role_admin", null);
        when(access.menuIdsOf(current)).thenReturn(List.of("menu_user"));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/admin/sysUsers/../sysMenus/add");
        req.setServletPath("/admin/sysMenus/add");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(req, res, new Object()));
        assertEquals(403, res.getStatus());
    }

    @Test
    void wallpaperByMenuComponent() throws Exception {
        current = user("mgr", "role_admin", null);
        when(access.menuIdsOf(current)).thenReturn(List.of("m_wall"));
        SysMenus m = new SysMenus();
        m.setId("m_wall");
        m.setComponent("tool/wallpaper/index");
        when(menus.listByIds(any())).thenReturn(List.of(m));
        assertEquals(200, call("GET", "/admin/wallpaper/group/list").getStatus());
        m.setComponent("tool/mindmap/index");
        assertEquals(403, call("GET", "/admin/wallpaper/group/list").getStatus());
    }

    @Test
    void optionsPreflightPasses() throws Exception {
        current = null;
        assertEquals(200, call("OPTIONS", "/admin/sysMenus/add").getStatus());
    }
}
