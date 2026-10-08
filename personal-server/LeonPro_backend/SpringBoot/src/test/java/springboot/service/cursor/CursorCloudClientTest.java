package springboot.service.cursor;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import springboot.utils.BizException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class CursorCloudClientTest {

    private static final String KEY = "cursor_test_key_value";

    @Test
    void createParsesAgentAndDoesNotEchoKeyOn401() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        CursorCloudClient client = new CursorCloudClient(http, "https://api.cursor.com", JsonMapper.builder().build());
        String basic = "Basic " + Base64.getEncoder().encodeToString((KEY + ":").getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo("https://api.cursor.com/v1/agents"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basic))
                .andRespond(withSuccess("""
                        {"agent":{"id":"bc-1","name":"readme","status":"ACTIVE","url":"https://cursor.com/agents/bc-1","latestRunId":"run-1"},
                         "run":{"id":"run-1","status":"CREATING"}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.cursor.com/v1/agents?limit=1"))
                .andRespond(withStatus(UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"nope " + KEY + "\"}"));

        CursorCloudClient.Created created = client.createAgent(KEY, "readme", "add readme", "https://github.com/a/b", "main", true);
        assertEquals("bc-1", created.agent().id());
        assertEquals("run-1", created.run().id());
        assertEquals("CREATING", created.run().status());

        BizException error = assertThrows(BizException.class, () -> client.verify(KEY));
        assertFalse(error.getMessage().contains(KEY));
        server.verify();
    }

    @Test
    void listAgentsFollowsNextCursor() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        CursorCloudClient client = new CursorCloudClient(http, "https://api.cursor.com", JsonMapper.builder().build());

        server.expect(requestTo("https://api.cursor.com/v1/agents?limit=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"items":[{"id":"bc-old","name":"已有任务","status":"IDLE","url":"https://cursor.com/agents/bc-old","latestRunId":"run-old","updatedAt":"2026-04-13T18:45:00.000Z"}],
                         "nextCursor":"bc-next"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.cursor.com/v1/agents?limit=99&cursor=bc-next"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"items":[{"id":"bc-older","name":"更早的任务","status":"ARCHIVED"}]}
                        """, MediaType.APPLICATION_JSON));

        java.util.List<CursorCloudClient.RemoteAgent> listed = client.listAgents(KEY, 100);
        assertEquals(2, listed.size());
        assertEquals("bc-old", listed.get(0).id());
        assertEquals("已有任务", listed.get(0).name());
        assertEquals("bc-older", listed.get(1).id());
        server.verify();
    }

    @Test
    void listRepositoriesParsesAndSkipsBlank() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        CursorCloudClient client = new CursorCloudClient(http, "https://api.cursor.com", JsonMapper.builder().build());

        server.expect(requestTo("https://api.cursor.com/v0/repositories"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"repositories":[
                          {"owner":"leon-lv","name":"leon-tools","repository":"https://github.com/leon-lv/leon-tools"},
                          {"owner":"leon-lv","name":"aubo-notes","repository":"https://github.com/leon-lv/aubo-notes"},
                          {"owner":"","name":"","repository":""}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        java.util.List<CursorCloudClient.RemoteRepo> repos = client.listRepositories(KEY);
        assertEquals(2, repos.size());
        assertEquals("aubo-notes", repos.get(0).name());
        assertEquals("https://github.com/leon-lv/leon-tools", repos.get(1).url());
        server.verify();
    }
}
