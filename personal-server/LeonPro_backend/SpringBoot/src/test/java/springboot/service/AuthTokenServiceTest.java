package springboot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthTokenServiceTest {

    private final Map<String, String> store = new HashMap<>();
    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private AuthTokenService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(ops).set(anyString(), anyString(), any(Duration.class));
        when(ops.get(any())).thenAnswer(inv -> store.get((String) inv.getArgument(0)));
        when(redis.delete(anyString())).thenAnswer(inv -> store.remove((String) inv.getArgument(0)) != null);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        service = new AuthTokenService(redis, env);
    }

    @Test
    void issuesRandomBase64UrlTokenStoredUnderHashedKey() {
        String t1 = service.issue("u1");
        String t2 = service.issue("u1");
        assertEquals(43, t1.length());
        assertTrue(t1.matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(t1, t2);
        String key = service.key(t1);
        assertTrue(key.matches("leonpro:dev:auth:token:[0-9a-f]{64}"), key);
        assertFalse(key.contains(t1));
        assertEquals("u1", store.get(key));
        verify(ops).set(eq(key), eq("u1"), eq(Duration.ofDays(7)));
    }

    @Test
    void resolveRenewsTtlAndRevokeInvalidates() {
        String t = service.issue("u1");
        assertEquals("u1", service.resolve(t));
        verify(redis).expire(service.key(t), Duration.ofDays(7));
        service.revoke(t);
        assertNull(service.resolve(t));
        assertNull(service.resolve("not-a-token"));
        assertNull(service.resolve(null));
        assertNull(service.resolve(" "));
    }

    @Test
    void bearerTokenParsing() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        assertNull(AuthTokenService.bearerToken(r));
        r.addHeader("Authorization", "bearer abc");
        assertEquals("abc", AuthTokenService.bearerToken(r));
        MockHttpServletRequest r2 = new MockHttpServletRequest();
        r2.addHeader("Authorization", "Basic abc");
        assertNull(AuthTokenService.bearerToken(r2));
        MockHttpServletRequest r3 = new MockHttpServletRequest();
        r3.addHeader("Authorization", "Bearer    ");
        assertNull(AuthTokenService.bearerToken(r3));
    }

    @Test
    @SuppressWarnings("unchecked")
    void revokeAllForUserDeletesOnlyThatUsersTokens() {
        String a1 = service.issue("u1");
        String a2 = service.issue("u1");
        String b = service.issue("u2");
        store.put("other:key", "u1");
        when(redis.scan(any(org.springframework.data.redis.core.ScanOptions.class))).thenAnswer(inv -> {
            java.util.Iterator<String> it = new java.util.ArrayList<>(store.keySet()).stream()
                    .filter(k -> k.startsWith(service.keyPrefix())).iterator();
            org.springframework.data.redis.core.Cursor<String> c = mock(org.springframework.data.redis.core.Cursor.class);
            when(c.hasNext()).thenAnswer(x -> it.hasNext());
            when(c.next()).thenAnswer(x -> it.next());
            return c;
        });
        when(ops.multiGet(any())).thenAnswer(inv -> ((java.util.Collection<String>) inv.getArgument(0)).stream()
                .map(store::get).toList());
        when(redis.delete(any(java.util.Collection.class))).thenAnswer(inv -> {
            long n = 0;
            for (Object k : (java.util.Collection<Object>) inv.getArgument(0)) {
                n += store.remove(k) != null ? 1 : 0;
            }
            return n;
        });
        assertEquals(2, service.revokeAllForUser("u1"));
        assertNull(service.resolve(a1));
        assertNull(service.resolve(a2));
        assertEquals("u2", service.resolve(b));
        assertEquals("u1", store.get("other:key"), "别的 key 不受影响");
        assertEquals(0, service.revokeAllForUser("u1"));
        assertEquals(0, service.revokeAllForUser(" "));
    }

    @Test
    void revokeAllForUserSwallowsRedisErrors() {
        when(redis.scan(any(org.springframework.data.redis.core.ScanOptions.class))).thenThrow(new IllegalStateException("down"));
        assertEquals(-1, service.revokeAllForUser("u1"));
    }
}
