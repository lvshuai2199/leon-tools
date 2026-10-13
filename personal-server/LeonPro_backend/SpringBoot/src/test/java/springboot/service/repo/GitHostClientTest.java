package springboot.service.repo;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import springboot.utils.BizException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class GitHostClientTest {

    private static final String TOKEN = "ghp_test_token_value_123456";

    @Test
    void unauthorizedDoesNotEchoToken() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        GitHostClient client = new GitHostClient(http, JsonMapper.builder().build());
        server.expect(requestTo("https://api.github.com/user"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withStatus(UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"bad " + TOKEN + "\"}"));

        BizException ex = assertThrows(BizException.class, () -> client.verify("github", TOKEN));
        assertFalse(ex.getMessage().contains(TOKEN));
        assertEquals("GitHub 令牌无效或没有仓库权限", ex.getMessage());
        server.verify();
    }

    @Test
    void graphqlFailureFallsBackToRestAndFillsLatestCommit() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        GitHostClient client = new GitHostClient(http, JsonMapper.builder().build());
        server.expect(requestTo("https://api.github.com/graphql"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"errors\":[{\"message\":\"nope\"}],\"data\":null}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/user"))
                .andRespond(withSuccess("{\"login\":\"leon-lv\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/user/repos?per_page=100&sort=pushed&direction=desc"
                        + "&affiliation=owner,collaborator,organization_member&page=1"))
                .andRespond(withSuccess("""
                        [{"name":"leon-tools","full_name":"leon-lv/leon-tools","private":true,"archived":false,
                          "description":"tools","html_url":"https://github.com/leon-lv/leon-tools","default_branch":"main",
                          "language":"Java","pushed_at":"2026-10-13T08:00:00Z","stargazers_count":1,"open_issues_count":0,
                          "owner":{"login":"leon-lv"}}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/repos/leon-lv/leon-tools/commits?sha=main&page=1&per_page=1"))
                .andRespond(withSuccess("""
                        [{"sha":"abc1234def567890","html_url":"https://github.com/leon-lv/leon-tools/commit/abc1234def567890",
                          "commit":{"message":"fix deploy\\n\\nmore","author":{"name":"Shuai","date":"2026-10-13T07:00:00Z"}},
                          "author":{"login":"leon-lv"}}]
                        """, MediaType.APPLICATION_JSON));

        GitHostClient.RemoteList list = client.listGithub(TOKEN);
        assertEquals("leon-lv", list.login());
        assertEquals(1, list.repos().size());
        assertEquals("fix deploy", list.repos().get(0).lastCommit().message());
        assertEquals("leon-lv", list.repos().get(0).lastCommit().author());
        server.verify();
    }

    @Test
    void giteeListUsesTokenHeader() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        GitHostClient client = new GitHostClient(http, JsonMapper.builder().build());
        server.expect(requestTo("https://gitee.com/api/v5/user"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "token " + TOKEN))
                .andRespond(withSuccess("{\"login\":\"sfnss\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gitee.com/api/v5/user/repos?type=all&sort=pushed&direction=desc&page=1&per_page=100"))
                .andRespond(withSuccess("""
                        [{"full_name":"sfnss/pic","name":"pic","private":false,"html_url":"https://gitee.com/sfnss/pic",
                          "default_branch":"master","pushed_at":"2026-10-12T10:00:00+08:00","namespace":{"path":"sfnss"}}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gitee.com/api/v5/repos/sfnss/pic/commits?sha=master&page=1&per_page=1"))
                .andRespond(withSuccess("""
                        [{"sha":"abc1234def","html_url":"https://gitee.com/sfnss/pic/commit/abc1234def",
                          "commit":{"message":"add pic\\n\\nbody","author":{"name":"帅","date":"2026-10-13T01:00:00+08:00"}},
                          "author":{"login":"sfnss"}}]
                        """, MediaType.APPLICATION_JSON));

        GitHostClient.RemoteList list = client.listGitee(TOKEN);
        assertEquals("sfnss", list.login());
        assertEquals("add pic", list.repos().get(0).lastCommit().message());
        assertEquals("sfnss", list.repos().get(0).lastCommit().author());
        server.verify();
    }
}
