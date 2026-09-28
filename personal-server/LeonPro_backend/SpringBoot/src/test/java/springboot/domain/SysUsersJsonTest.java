package springboot.domain;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import springboot.utils.RequestUserUtils;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SysUsersJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void passwordIsWriteOnlyAndTokenOnlyWhenPresent() {
        SysUsers u = new SysUsers();
        u.setId("u1");
        u.setUsername("admin");
        u.setPassword("secret-value");
        String json = mapper.writeValueAsString(u);
        assertFalse(json.contains("password"), json);
        assertFalse(json.contains("secret-value"), json);
        assertFalse(json.contains("\"token\""), json);
        u.setToken("tkn");
        assertTrue(mapper.writeValueAsString(u).contains("\"token\":\"tkn\""));
    }

    @Test
    void passwordIsStillAcceptedOnInput() {
        SysUsers u = mapper.readValue("{\"username\":\"a\",\"password\":\"p1\"}", SysUsers.class);
        assertEquals("p1", u.getPassword());
    }

    @Test
    void requestUserUtilsIgnoresClientHeaders() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.addHeader("X-User-Id", "u-admin");
        r.addHeader("X-Username", "admin");
        assertNull(RequestUserUtils.currentUserId(r));
        assertNull(RequestUserUtils.currentUsername(r));
        r.setAttribute(RequestUserUtils.ATTR_USER_ID, "u-real");
        r.setAttribute(RequestUserUtils.ATTR_USERNAME, "real");
        assertEquals("u-real", RequestUserUtils.currentUserId(r));
        assertEquals("real", RequestUserUtils.currentUsername(r));
    }
}
