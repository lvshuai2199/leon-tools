package springboot.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import springboot.domain.SysRoles;
import springboot.domain.SysUsers;
import springboot.service.AuthTokenService;
import springboot.service.SysRolesService;
import springboot.service.SysUsersService;
import springboot.utils.ApiResponse;
import springboot.utils.RequestUserUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(AuthInterceptorTest.TestConfig.class)
@ActiveProfiles("dev")
class AuthInterceptorTest {

    static final Map<String, String> REDIS = new ConcurrentHashMap<>();

    @Autowired
    WebApplicationContext wac;
    @Autowired
    AuthTokenService tokens;
    @Autowired
    SysUsersService users;
    @Autowired
    SysRolesService roles;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        REDIS.clear();
        reset(users, roles);
        when(users.getById("u-admin")).thenReturn(user("u-admin", "admin", "role_root"));
        when(users.getById("u-sub")).thenReturn(user("u-sub", "sub", "role_sub"));
        when(users.getById("u-off")).thenReturn(user("u-off", "off", "role_off"));
        SysRoles off = new SysRoles();
        off.setId("role_off");
        off.setIsDisabled(1);
        when(roles.getById("role_off")).thenReturn(off);
        mvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    private static SysUsers user(String id, String name, String roleId) {
        SysUsers u = new SysUsers();
        u.setId(id);
        u.setUsername(name);
        u.setRoleId(roleId);
        return u;
    }

    private static void expect401(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        r.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("登录已失效，请重新登录"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void noTokenIs401WithWrapperBody() throws Exception {
        expect401(mvc.perform(get("/wallpaper/group/list")));
    }

    @Test
    void forgedOrMalformedTokenIs401() throws Exception {
        expect401(mvc.perform(get("/wallpaper/group/list").header("Authorization", "Bearer forged-token-123")));
        expect401(mvc.perform(get("/wallpaper/group/list").header("Authorization", "Bearer ")));
        expect401(mvc.perform(get("/wallpaper/group/list").header("Authorization", "Token " + tokens.issue("u-admin"))));
    }

    @Test
    void headerOnlySpoofIs401() throws Exception {
        expect401(mvc.perform(get("/wallpaper/group/list").header("X-Username", "admin")));
        expect401(mvc.perform(get("/wallpaper/group/list").header("X-User-Id", "u-admin").header("X-Username", "admin")));
    }

    @Test
    void revokedTokenIs401() throws Exception {
        String t = tokens.issue("u-admin");
        mvc.perform(get("/wallpaper/group/list").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("u-admin"));
        tokens.revoke(t);
        expect401(mvc.perform(get("/wallpaper/group/list").header("Authorization", "Bearer " + t)));
    }

    @Test
    void validTokenWithSpoofedHeadersResolvesToTokenOwner() throws Exception {
        String t = tokens.issue("u-sub");
        mvc.perform(get("/wallpaper/group/list")
                        .header("Authorization", "Bearer " + t)
                        .header("X-User-Id", "u-admin")
                        .header("X-Username", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("u-sub"))
                .andExpect(jsonPath("$.data.username").value("sub"));
    }

    @Test
    void tokenOfDeletedUserOrDisabledRoleIs401() throws Exception {
        expect401(mvc.perform(get("/wallpaper/group/list").header("Authorization", "Bearer " + tokens.issue("u-gone"))));
        expect401(mvc.perform(get("/wallpaper/group/list").header("Authorization", "Bearer " + tokens.issue("u-off"))));
    }

    @Test
    void otherAdminModulesIncludingExternAccountsAreProtected() throws Exception {
        expect401(mvc.perform(get("/sysUsers/getAllUsers")));
        expect401(mvc.perform(get("/externAccounts/getAll")));
        expect401(mvc.perform(get("/externWallet/getAll").header("X-Username", "admin")));
        expect401(mvc.perform(post("/auth/logout")));
    }

    @Test
    void exemptPathsNeedNoToken() throws Exception {
        for (String p : new String[]{"/auth/login", "/auth/login2", "/auth/captcha"}) {
            mvc.perform(post(p)).andExpect(status().isOk()).andExpect(jsonPath("$.data").value("open"));
        }
        for (String p : new String[]{"/extern/wallpaper/random", "/public/crabShipment/abc",
                "/public/mindmap/abc.png", "/uploads/wallpaper/g/a.jpg", "/wechat/oa"}) {
            mvc.perform(get(p)).andExpect(status().isOk()).andExpect(jsonPath("$.data").value("open"));
        }
    }

    @Test
    void optionsPreflightIsNotBlocked() throws Exception {
        mvc.perform(options("/wallpaper/group/list")
                        .header("Origin", "http://example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().is(not(401)));
    }

    @RestController
    static class ProbeController {
        @GetMapping({"/wallpaper/group/list", "/sysUsers/getAllUsers", "/externAccounts/getAll", "/externWallet/getAll"})
        ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
            Map<String, Object> m = new HashMap<>();
            m.put("userId", RequestUserUtils.currentUserId(request));
            m.put("username", RequestUserUtils.currentUsername(request));
            return ApiResponse.success(m);
        }

        @PostMapping({"/auth/login", "/auth/login2", "/auth/captcha", "/auth/logout"})
        ApiResponse<String> openPost() {
            return ApiResponse.success("open");
        }

        @GetMapping({"/extern/wallpaper/random", "/public/crabShipment/abc", "/public/mindmap/abc.png",
                "/uploads/wallpaper/g/a.jpg", "/wechat/oa"})
        ApiResponse<String> openGet() {
            return ApiResponse.success("open");
        }
    }

    @Configuration
    @EnableWebMvc
    @Import({AuthWebConfig.class, AuthInterceptor.class, ProbeController.class})
    static class TestConfig {

        @Bean
        @SuppressWarnings("unchecked")
        StringRedisTemplate stringRedisTemplate() {
            StringRedisTemplate redis = mock(StringRedisTemplate.class);
            ValueOperations<String, String> ops = mock(ValueOperations.class);
            when(redis.opsForValue()).thenReturn(ops);
            org.mockito.Mockito.doAnswer(inv -> {
                REDIS.put(inv.getArgument(0), inv.getArgument(1));
                return null;
            }).when(ops).set(anyString(), anyString(), any(Duration.class));
            when(ops.get(any())).thenAnswer(inv -> REDIS.get((String) inv.getArgument(0)));
            when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);
            when(redis.delete(anyString())).thenAnswer(inv -> REDIS.remove((String) inv.getArgument(0)) != null);
            return redis;
        }

        @Bean
        AuthTokenService authTokenService(StringRedisTemplate redis, Environment environment) {
            return new AuthTokenService(redis, environment);
        }

        @Bean
        SysUsersService sysUsersService() {
            return mock(SysUsersService.class);
        }

        @Bean
        SysRolesService sysRolesService() {
            return mock(SysRolesService.class);
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }
}
