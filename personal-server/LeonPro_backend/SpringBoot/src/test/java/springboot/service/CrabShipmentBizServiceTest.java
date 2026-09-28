package springboot.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import springboot.domain.CrabShipment;
import springboot.domain.SysUsers;
import springboot.utils.ApiResponse;
import springboot.utils.ForbiddenException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrabShipmentBizServiceTest {

    CrabShipmentService crab;
    SysUsersService users;
    RegCodeAccessService access;
    CrabShipmentBizService biz;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUsers.class);
        TableInfoHelper.initTableInfo(assistant, CrabShipment.class);
    }

    @BeforeEach
    void setUp() {
        crab = mock(CrabShipmentService.class);
        users = mock(SysUsersService.class);
        access = mock(RegCodeAccessService.class);
        biz = new CrabShipmentBizService(crab, users, access);
        when(access.isRootUser(any(SysUsers.class))).thenAnswer(inv -> {
            SysUsers u = inv.getArgument(0);
            return u != null && "role_root".equals(u.getRoleId());
        });
        when(access.isSubAccount(any())).thenAnswer(inv -> {
            SysUsers u = inv.getArgument(0);
            return u != null && u.getParentId() != null && !u.getParentId().isBlank();
        });
    }

    static SysUsers u(String id, String roleId, String parentId) {
        SysUsers x = new SysUsers();
        x.setId(id);
        x.setUsername(id);
        x.setRoleId(roleId);
        x.setParentId(parentId);
        return x;
    }

    static CrabShipment rec(String id, String operatorId) {
        CrabShipment c = new CrabShipment();
        c.setId(id);
        c.setOperatorId(operatorId);
        c.setPublicId("pub-" + id + "-0000");
        return c;
    }

    @Test
    void scopes() {
        assertNull(biz.scopeOf(u("root", "role_root", null)), "ROOT 不限");
        assertEquals(Set.of("s1"), biz.scopeOf(u("s1", "role_x", "main")), "子账号只有自己");
        when(users.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(u("s1", "role_x", "main"), u("s2", "role_x", "main")));
        assertEquals(Set.of("main", "s1", "s2"), biz.scopeOf(u("main", "role_x", null)), "主账号 = 自己 + 子账号");
        assertThrows(ForbiddenException.class, () -> biz.scopeOf(null));
    }

    @Test
    void outOfScopeIs403() {
        when(crab.getById("mine")).thenReturn(rec("mine", "main"));
        when(crab.getById("other")).thenReturn(rec("other", "someone"));
        when(crab.getById("legacy")).thenReturn(rec("legacy", null));
        Set<String> scope = Set.of("main", "s1");

        assertEquals(200, biz.detail("mine", scope).getStatus());
        assertThrows(ForbiddenException.class, () -> biz.detail("other", scope));
        assertThrows(ForbiddenException.class, () -> biz.detail("legacy", scope), "operator_id 为空的旧数据只有 ROOT / 管理端能看");
        assertEquals(200, biz.detail("legacy", null).getStatus());

        CrabShipment body = rec("other", null);
        body.setCustomerName("张三");
        assertThrows(ForbiddenException.class, () -> biz.save(body, u("main", "role_x", null), scope));
        assertThrows(ForbiddenException.class, () -> biz.updateStatus(rec("other", null), scope));
    }

    @Test
    void deleteWithAnyOutOfScopeIdIsWhollyRejected() {
        when(crab.listByIds(any())).thenReturn(List.of(rec("a", "main"), rec("b", "someone")));
        assertThrows(ForbiddenException.class, () -> biz.delete(List.of("a", "b"), Set.of("main")));
        verify(crab, never()).removeByIds(any(java.util.Collection.class));
        when(crab.listByIds(any())).thenReturn(List.of(rec("a", "main")));
        when(crab.removeByIds(any(java.util.Collection.class))).thenReturn(true);
        assertEquals(200, biz.delete(List.of("a"), Set.of("main")).getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicViewMasksPhoneAndAddress() {
        CrabShipment c = rec("x", "main");
        c.setPublicId("abcdef1234");
        c.setPhone("13812345678");
        c.setAddress("文三路88号 电话 13987654321");
        c.setCustomerName("张三");
        when(crab.getOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class), anyBoolean())).thenReturn(c);
        ApiResponse<?> r = biz.publicView("abcdef1234");
        Map<String, Object> view = (Map<String, Object>) r.getData();
        assertEquals("138****5678", view.get("phone"));
        assertEquals("文三路88号 电话 139****4321", view.get("address"));
        assertEquals(200, r.getStatus());
    }

    @Test
    void newRecordsBelongToCurrentUserAndShareUsesHistoryPath() {
        when(crab.getOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class), anyBoolean())).thenReturn(null);
        when(crab.save(any())).thenReturn(true);
        CrabShipment body = new CrabShipment();
        body.setCustomerName("李四");
        SysUsers me = u("s1", "role_x", "main");
        me.setNickname("小王");
        ApiResponse<?> r = biz.save(body, me, Set.of("s1"));
        CrabShipment saved = (CrabShipment) r.getData();
        assertEquals("s1", saved.getOperatorId());
        assertEquals("小王", saved.getOperatorName());
        assertEquals("/s/crab/" + saved.getPublicId(), saved.getSharePath());
    }
}
