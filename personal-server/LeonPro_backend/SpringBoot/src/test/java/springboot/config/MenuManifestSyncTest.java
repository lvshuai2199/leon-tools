package springboot.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import springboot.service.menu.MenuSyncPlan;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MenuManifestSyncTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager tm = mock(PlatformTransactionManager.class);

    private MenuManifestSync sync(String mode) {
        TransactionStatus status = new SimpleTransactionStatus();
        when(tm.getTransaction(any())).thenReturn(status);
        MenuManifestSync s = new MenuManifestSync(jdbc, tm, new DefaultResourceLoader());
        s.mode = mode;
        s.adminManifest = "classpath:menus/admin/not-there.json";
        s.appManifest = "classpath:menus/app/not-there.json";
        return s;
    }

    @Test
    void offDoesNothing() {
        sync("off").run();
        verifyNoInteractions(jdbc);
    }

    @Test
    void missingManifestSkipsWithoutTouchingMenus() {
        MenuManifestSync s = sync("apply");
        MenuSyncPlan plan = s.syncClient("app", s.appManifest, true);
        assertNull(plan);
        verifyNoInteractions(jdbc);
    }

    @Test
    void grantSkippedWhenMarkerExists() {
        MenuManifestSync s = sync("apply");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(MenuManifestSync.MARKER_APP_CRAB))).thenReturn(1);
        s.grantOnce(MenuManifestSync.MARKER_APP_CRAB, "/crab", "menu_crab", Set.of("role_root"), true);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void grantWaitsForAppMenuAndWritesNoMarker() {
        MenuManifestSync s = sync("apply");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(MenuManifestSync.MARKER_APP_CRAB))).thenReturn(0);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("app"), eq("/crab"))).thenReturn(List.of());
        s.grantOnce(MenuManifestSync.MARKER_APP_CRAB, "/crab", "menu_crab", Set.of("role_root"), true);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void grantInsertsForSourceRolesExceptExcludedAndAlreadyGranted() {
        MenuManifestSync s = sync("apply");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(MenuManifestSync.MARKER_APP_CRAB))).thenReturn(0);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("app"), eq("/crab"))).thenReturn(List.of("menu_app_crab"));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("menu_crab")))
                .thenReturn(List.of("role_root", "role_regcode_client", "role_ops", "role_boss"));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("menu_app_crab"))).thenReturn(List.of("role_boss"));
        s.grantOnce(MenuManifestSync.MARKER_APP_CRAB, "/crab", "menu_crab",
                Set.of("role_root", "role_regcode_client"), true);
        verify(jdbc).update(eq("INSERT INTO sys_role_menu (id, rold_id, menu_id) VALUES (?, ?, ?)"),
                anyString(), eq("role_ops"), eq("menu_app_crab"));
        verify(jdbc, never()).update(eq("INSERT INTO sys_role_menu (id, rold_id, menu_id) VALUES (?, ?, ?)"),
                anyString(), eq("role_regcode_client"), anyString());
        verify(jdbc, never()).update(eq("INSERT INTO sys_role_menu (id, rold_id, menu_id) VALUES (?, ?, ?)"),
                anyString(), eq("role_boss"), anyString());
        verify(jdbc).update(eq("INSERT INTO sys_setup_marker (marker_key, done_at, note) VALUES (?, ?, ?)"),
                eq(MenuManifestSync.MARKER_APP_CRAB), any(), anyString());
    }

    @Test
    void dryRunGrantWritesNothing() {
        MenuManifestSync s = sync("dry-run");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(MenuManifestSync.MARKER_APP_REGCODE))).thenReturn(0);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("app"), eq("/regcode"))).thenReturn(List.of("menu_app_regcode"));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("menu_regcode"))).thenReturn(List.of("role_regcode_client"));
        s.grantOnce(MenuManifestSync.MARKER_APP_REGCODE, "/regcode", "menu_regcode", Set.of("role_root"), false);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void parseRoleListSplitsAndDedups() {
        assertEquals(List.of("role_a", "role_b", "role_c"), MenuManifestSync.parseRoleList(" role_a, role_b，role_a ;role_c "));
        assertEquals(List.of(), MenuManifestSync.parseRoleList(""));
        assertEquals(List.of(), MenuManifestSync.parseRoleList(null));
    }

    private static final String INSERT_GRANT = "INSERT INTO sys_role_menu (id, rold_id, menu_id) VALUES (?, ?, ?)";
    private static final String INSERT_MARKER = "INSERT INTO sys_setup_marker (marker_key, done_at, note) VALUES (?, ?, ?)";

    private void stubListedRoles() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq("app"), eq("/crab"))).thenReturn(List.of("menu_app_crab"));
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM sys_setup_marker"), eq(Integer.class), anyString())).thenReturn(0);
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM sys_setup_marker"), eq(Integer.class),
                eq(MenuManifestSync.MARKER_APP_CRAB_ROLE_PREFIX + "role_done"))).thenReturn(1);
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM sys_roles"), eq(Integer.class), anyString())).thenReturn(0);
        for (String r : List.of("role_ops", "role_done", "role_has")) {
            when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM sys_roles"), eq(Integer.class), eq(r))).thenReturn(1);
        }
        when(jdbc.queryForList(anyString(), eq(String.class), anyString(), eq("menu_app_crab"))).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(String.class), eq("role_has"), eq("menu_app_crab"))).thenReturn(List.of("rm1"));
    }

    @Test
    void listedRolesGrantedOncePerRole() {
        MenuManifestSync s = sync("apply");
        stubListedRoles();
        s.grantToListedRoles("/crab", "role_ops, role_root, role_regcode_client, role_ghost, role_done, role_has",
                Set.of("role_root", "role_regcode_client"), true, Set.of());
        verify(jdbc).update(eq(INSERT_GRANT), anyString(), eq("role_ops"), eq("menu_app_crab"));
        verify(jdbc).update(eq(INSERT_MARKER), eq(MenuManifestSync.MARKER_APP_CRAB_ROLE_PREFIX + "role_ops"), any(), anyString());
        verify(jdbc, never()).update(eq(INSERT_GRANT), anyString(), eq("role_has"), anyString());
        verify(jdbc).update(eq(INSERT_MARKER), eq(MenuManifestSync.MARKER_APP_CRAB_ROLE_PREFIX + "role_has"), any(), anyString());
        for (String r : List.of("role_root", "role_regcode_client", "role_ghost", "role_done")) {
            verify(jdbc, never()).update(eq(INSERT_GRANT), anyString(), eq(r), anyString());
            verify(jdbc, never()).update(eq(INSERT_MARKER), eq(MenuManifestSync.MARKER_APP_CRAB_ROLE_PREFIX + r), any(), anyString());
        }
    }

    @Test
    void listedRolesDryRunAndEmptyListWriteNothing() {
        MenuManifestSync s = sync("dry-run");
        stubListedRoles();
        s.grantToListedRoles("/crab", "role_ops,role_has", Set.of("role_root"), false, Set.of());
        s.grantToListedRoles("/crab", "", Set.of("role_root"), true, Set.of());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void listedRolesWaitForAppMenu() {
        MenuManifestSync s = sync("apply");
        when(jdbc.queryForList(anyString(), eq(String.class), eq("app"), eq("/crab"))).thenReturn(List.of());
        s.grantToListedRoles("/crab", "role_ops", Set.of("role_root"), true, Set.of());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
