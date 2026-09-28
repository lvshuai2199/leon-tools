package springboot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import springboot.DTO.RegCodeSubUser;
import springboot.domain.ComRegistration;
import springboot.domain.SysUsers;
import springboot.utils.BizException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegCodeQuotaServiceTest {

    JdbcTemplate jdbc;
    ComRegistrationService records;
    RegCodeAccessService access;
    SysUsersService users;
    RegCodeUserService regUsers;
    RegCodeQuotaService svc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        records = mock(ComRegistrationService.class);
        access = mock(RegCodeAccessService.class);
        users = mock(SysUsersService.class);
        regUsers = mock(RegCodeUserService.class);
        svc = new RegCodeQuotaService(jdbc, users, regUsers, mock(RegCodeUserConfigService.class),
                mock(RegCodeConfigService.class), records, access);
    }

    static SysUsers u(String id) {
        SysUsers x = new SysUsers();
        x.setId(id);
        x.setUsername(id);
        return x;
    }

    @Test
    void generationDeductsWithConditionalUpdateAndRollsBackWhenExhausted() {
        when(jdbc.queryForList(startsWith("SELECT id FROM reg_code_user_config"), eq(String.class), any(Object[].class)))
                .thenReturn(List.of("row1"));
        when(jdbc.update(startsWith("UPDATE reg_code_user_config SET generate_used = generate_used + 1"), any(Object[].class)))
                .thenReturn(0);
        assertThrows(BizException.class, () -> svc.consumeAndRecord(u("c"), "cfg", new ComRegistration()));
        verify(records, never()).save(any());

        when(jdbc.update(startsWith("UPDATE reg_code_user_config SET generate_used = generate_used + 1"), any(Object[].class)))
                .thenReturn(1);
        svc.consumeAndRecord(u("c"), "cfg", new ComRegistration());
        verify(records).save(any());
    }

    @Test
    void rootIsNotDeducted() {
        SysUsers root = u("root");
        when(access.isRootUser(root)).thenReturn(true);
        svc.consumeAndRecord(root, "cfg", new ComRegistration());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(records).save(any());
    }

    @Test
    void createRejectedWhenEnabledSubUsersReachMax() {
        when(jdbc.queryForList(startsWith("SELECT max_sub_users"), eq(Integer.class), any(Object[].class))).thenReturn(List.of(2));
        when(access.enabledSubUserCount("c")).thenReturn(2);
        RegCodeSubUser.CreateForm f = new RegCodeSubUser.CreateForm();
        f.setUsername("sub01");
        f.setPassword("123456");
        BizException e = assertThrows(BizException.class, () -> svc.createSubUser(u("c"), f));
        assertTrue(e.getMessage().contains("上限"), e.getMessage());
        verify(users, never()).save(any());
    }

    @Test
    void allocationBeyondCreatorRemainingFails() {
        when(jdbc.queryForList(startsWith("SELECT max_sub_users"), eq(Integer.class), any(Object[].class))).thenReturn(List.of(1));
        when(jdbc.queryForList(startsWith("SELECT id FROM reg_code_user_config"), eq(String.class), any(Object[].class)))
                .thenReturn(List.of("creatorRow"));
        when(jdbc.update(startsWith("UPDATE reg_code_user_config SET generate_limit = generate_limit - ?"), any(Object[].class)))
                .thenReturn(0);
        RegCodeSubUser.DeltaItem d = new RegCodeSubUser.DeltaItem();
        d.setConfigId("cfg");
        d.setDelta(5);
        BizException e = assertThrows(BizException.class, () -> svc.adjustByCreator(u("c"), u("s"), List.of(d)));
        assertTrue(e.getMessage().contains("剩余次数不足"), e.getMessage());
    }

    @Test
    void disablingRefundsUnusedToCreator() {
        when(jdbc.queryForList(startsWith("SELECT max_sub_users"), eq(Integer.class), any(Object[].class))).thenReturn(List.of(3));
        when(jdbc.update(startsWith("UPDATE reg_code_user SET status"), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(startsWith("SELECT id, config_id"), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", "subRow", "config_id", "cfg", "remaining", 3)));
        when(jdbc.queryForList(startsWith("SELECT id FROM reg_code_user_config"), eq(String.class), any(Object[].class)))
                .thenReturn(List.of("creatorRow"));
        when(jdbc.update(startsWith("UPDATE reg_code_user_config SET generate_limit = generate_limit - ?"), any(Object[].class)))
                .thenReturn(1);
        RegCodeSubUser.StatusResult r = svc.setStatus(u("c"), u("s"), 0);
        assertEquals(3, r.getRefundedTotal());
        assertEquals("cfg", r.getRefunded().get(0).getConfigId());
        verify(jdbc).update(startsWith("UPDATE reg_code_user_config SET generate_limit = generate_limit + ?"), eq(3), eq("creatorRow"));
    }

    @Test
    void retireSubUsersDisablesAndVoidsButKeepsAccounts() {
        when(users.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(u("s1"), u("s2")));
        when(jdbc.update(startsWith("UPDATE reg_code_user SET status"), eq(0), any(), eq("s1"))).thenReturn(1);
        when(jdbc.update(startsWith("UPDATE reg_code_user SET status"), eq(0), any(), eq("s2"))).thenReturn(0);
        when(jdbc.queryForObject(startsWith("SELECT COALESCE(SUM(generate_limit - generate_used)"), eq(Integer.class), eq("s1"))).thenReturn(7);
        when(jdbc.queryForObject(startsWith("SELECT COALESCE(SUM(generate_limit - generate_used)"), eq(Integer.class), eq("s2"))).thenReturn(0);
        RegCodeQuotaService.RetireResult r = svc.retireSubUsers("cust");
        assertEquals(List.of("s1", "s2"), r.getUserIds());
        assertEquals(7, r.getVoidedTotal());
        verify(jdbc).update(startsWith("UPDATE reg_code_user_config SET generate_limit = generate_used"), eq("s1"));
        verify(jdbc).update(startsWith("UPDATE reg_code_user_config SET generate_limit = generate_used"), eq("s2"));
        // s2 没有 reg_code_user 行：补一条停用行，保证登录被拒
        verify(regUsers).save(org.mockito.ArgumentMatchers.argThat(row -> "s2".equals(row.getUserId()) && row.getStatus() == 0));
        verify(users, never()).removeByIds(any(java.util.Collection.class));
        verify(users, never()).removeById(anyString());
        // 不退回任何人
        verify(jdbc, never()).update(startsWith("UPDATE reg_code_user_config SET generate_limit = generate_limit +"), any(Object[].class));
        assertTrue(svc.retireSubUsers(null).getUserIds().isEmpty());
    }
}
