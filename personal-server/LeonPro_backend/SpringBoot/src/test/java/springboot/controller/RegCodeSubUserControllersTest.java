package springboot.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import springboot.DTO.RegCodeSubUser;
import springboot.controller.web.CommonRegCodeController;
import springboot.controller.web.RegCodeUserController;
import springboot.domain.RegCodeUser;
import springboot.domain.SysUsers;
import springboot.service.AuthTokenService;
import springboot.service.RegCodeAccessService;
import springboot.service.RegCodeConfigService;
import springboot.service.RegCodeQuotaService;
import springboot.service.RegCodeUserService;
import springboot.service.SysRolesService;
import springboot.service.SysUsersService;
import springboot.utils.ApiResponse;
import springboot.utils.ForbiddenException;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 子用户停用 / 重置密码吊销 token；管理端客户 id 两种都认；删除客户不删子用户 */
class RegCodeSubUserControllersTest {

    SysUsersService users;
    RegCodeUserService regUsers;
    RegCodeAccessService access;
    RegCodeQuotaService quota;
    AuthTokenService tokens;
    RegCodeUserController admin;
    CommonRegCodeController common;
    MockHttpServletRequest req = new MockHttpServletRequest();

    static SysUsers u(String id, String roleId, String parentId) {
        SysUsers x = new SysUsers();
        x.setId(id);
        x.setUsername(id);
        x.setRoleId(roleId);
        x.setParentId(parentId);
        return x;
    }

    static RegCodeUser row(String id, String userId) {
        RegCodeUser r = new RegCodeUser();
        r.setId(id);
        r.setUserId(userId);
        return r;
    }

    SysUsers root = u("root", "role_root", null);
    SysUsers cust = u("cust", "role_regcode_client", "root");
    SysUsers sub = u("sub", "role_regcode_client", "cust");

    @BeforeEach
    void setUp() {
        users = mock(SysUsersService.class);
        regUsers = mock(RegCodeUserService.class);
        access = mock(RegCodeAccessService.class);
        quota = mock(RegCodeQuotaService.class);
        tokens = mock(AuthTokenService.class);
        admin = new RegCodeUserController(regUsers, access, mock(RegCodeConfigService.class), users,
                mock(SysRolesService.class), quota, tokens);
        common = new CommonRegCodeController(access, mock(RegCodeConfigService.class), quota, users, tokens);
        when(access.currentUser(any())).thenReturn(root);
        when(access.isRootUser(any(SysUsers.class))).thenAnswer(inv -> inv.getArgument(0) == root);
        when(users.getById("root")).thenReturn(root);
        when(users.getById("cust")).thenReturn(cust);
        when(users.getById("sub")).thenReturn(sub);
        when(regUsers.getById("row-cust")).thenReturn(row("row-cust", "cust"));
        when(regUsers.getById("row-sub")).thenReturn(row("row-sub", "sub"));
        when(access.getAssignment("cust")).thenReturn(row("row-cust", "cust"));
        when(access.isBottomSubUser(sub)).thenReturn(true);
        when(quota.subUserList(any())).thenReturn(new RegCodeSubUser.SubUserList());
        when(quota.retireSubUsers(anyString())).thenAnswer(inv -> new RegCodeQuotaService.RetireResult());
    }

    @Test
    void subUserListAcceptsUserIdOrRowIdAndUnknownIs404NotServerError() {
        assertEquals(200, admin.listSubUsers("cust", req).getStatus());
        assertEquals(200, admin.listSubUsers("row-cust", req).getStatus(), "列表里的 id（reg_code_user.id）也认");
        verify(quota, org.mockito.Mockito.times(2)).subUserList(cust);
        ApiResponse r = admin.listSubUsers("nope", req);
        assertEquals(404, r.getStatus());
        assertEquals(404, admin.listSubUsers(" ", req).getStatus());
    }

    @Test
    void subUserQuotaEndpointsAcceptRowIdToo() {
        when(quota.subUserQuota(any(), any())).thenReturn(new RegCodeSubUser.SubUserQuota());
        assertEquals(200, admin.subUserQuota("row-sub", req).getStatus());
        verify(quota).subUserQuota(sub, cust);
        assertThrows(ForbiddenException.class, () -> admin.subUserQuota("nope", req));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deletingCustomerRetiresSubUsersInsteadOfDeletingThem() {
        when(access.isAdminAccount(cust)).thenReturn(false);
        RegCodeQuotaService.RetireResult rr = new RegCodeQuotaService.RetireResult();
        rr.getUserIds().add("sub");
        rr.setVoidedTotal(5);
        when(quota.retireSubUsers("cust")).thenReturn(rr);
        when(regUsers.removeByIds(any(Collection.class))).thenReturn(true);
        ApiResponse r = admin.delete(List.of("cust"), req);   // 传 userId 也认
        assertEquals(200, r.getStatus());
        Map<String, Object> data = (Map<String, Object>) r.getData();
        assertEquals(1, data.get("retiredSubUsers"));
        assertEquals(5, data.get("voidedTotal"));
        verify(quota).removeAll("cust");
        verify(regUsers).removeByIds(argThat((Collection<String> ids) -> ids.size() == 1 && ids.contains("row-cust")));
        verify(users).removeByIds(argThat((Collection<String> ids) -> ids.contains("cust") && !ids.contains("sub")));
        verify(quota, never()).removeAll("sub");
        verify(tokens).revokeAllForUser("sub");
        verify(tokens).revokeAllForUser("cust");
        verify(users, never()).list(any(Wrapper.class));
    }

    @Test
    void deleteWithUnknownIdIs404AndDeletesNothing() {
        ApiResponse r = admin.delete(List.of("row-cust", "nope"), req);
        assertEquals(404, r.getStatus());
        verify(quota, never()).removeAll(anyString());
        verify(regUsers, never()).removeByIds(any(Collection.class));
    }

    @Test
    void disablingOrResettingSubUserRevokesItsTokens() {
        when(access.requireRegCode(any())).thenReturn(cust);
        when(access.canManageSubUsers(cust)).thenReturn(true);
        RegCodeSubUser.StatusForm off = new RegCodeSubUser.StatusForm();
        off.setStatus(0);
        when(quota.setStatus(eq(cust), eq(sub), eq(0))).thenReturn(new RegCodeSubUser.StatusResult());
        common.setSubUserStatus("sub", off, req);
        verify(tokens).revokeAllForUser("sub");

        tokens = mock(AuthTokenService.class);
        common = new CommonRegCodeController(access, mock(RegCodeConfigService.class), quota, users, tokens);
        RegCodeSubUser.StatusForm on = new RegCodeSubUser.StatusForm();
        on.setStatus(1);
        when(quota.setStatus(eq(cust), eq(sub), eq(1))).thenReturn(new RegCodeSubUser.StatusResult());
        common.setSubUserStatus("sub", on, req);
        verify(tokens, never()).revokeAllForUser(anyString());

        when(quota.resetPassword(sub)).thenReturn("NewPass1234");
        ApiResponse r = common.resetSubUserPassword("sub", req);
        assertTrue(r.getData().toString().contains("NewPass1234"));
        verify(tokens).revokeAllForUser("sub");
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonRootAdminGets403ForCustomersThatAreNotTheirs() {
        SysUsers tAdmin = u("t_admin", "role_admin", null);
        SysUsers mine = u("mine", "role_regcode_client", "t_admin");
        when(users.getById("t_admin")).thenReturn(tAdmin);
        when(users.getById("mine")).thenReturn(mine);
        when(access.currentUser(any())).thenReturn(tAdmin);

        // 删除 / 修改别人的客户：HTTP 403（ForbiddenException → GlobalExceptionHandler），说“客户”
        ForbiddenException e = assertThrows(ForbiddenException.class, () -> admin.delete(List.of("row-cust"), req));
        assertEquals("只能管理自己账户下的客户", e.getMessage());
        verify(regUsers, never()).removeByIds(any(Collection.class));
        verify(quota, never()).removeAll(anyString());
        verify(quota, never()).retireSubUsers(anyString());

        springboot.DTO.RegCodeUserForm form = new springboot.DTO.RegCodeUserForm();
        form.setId("row-cust");
        e = assertThrows(ForbiddenException.class, () -> admin.update(form, req), "表单不完整也先判归属");
        assertEquals("只能管理自己账户下的客户", e.getMessage());
        verify(regUsers, never()).updateById(any());
        verify(quota, never()).replaceCustomerQuotas(anyString(), any());

        e = assertThrows(ForbiddenException.class, () -> admin.listSubUsers("cust", req));
        assertEquals("只能管理自己账户下的客户", e.getMessage());

        // 目标是子用户（创建人不是自己的客户）：说“子用户”
        e = assertThrows(ForbiddenException.class, () -> admin.subUserQuota("sub", req));
        assertEquals("只能管理自己账户下的子用户", e.getMessage());
        e = assertThrows(ForbiddenException.class, () -> admin.adjustSubUserQuota("sub", new RegCodeSubUser.DeltaForm(), req));
        assertEquals("只能管理自己账户下的子用户", e.getMessage());
        verify(quota, never()).adjustByAdmin(any(), any());

        // 自己的客户正常
        assertEquals(200, admin.listSubUsers("mine", req).getStatus());
        assertEquals(404, admin.delete(List.of("nope"), req).getStatus(), "不存在仍是 404");
    }
}
