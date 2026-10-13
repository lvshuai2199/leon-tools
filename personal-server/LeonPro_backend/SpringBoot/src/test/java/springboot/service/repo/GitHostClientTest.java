package springboot.service.repo;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import springboot.DTO.RepoViews.BranchPage;
import springboot.DTO.RepoViews.GraphPage;
import springboot.DTO.RepoViews.MergeResult;
import springboot.utils.BizException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.CONFLICT;
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

    @Test
    void githubBranchesCompareAgainstDefault() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        GitHostClient client = new GitHostClient(http, JsonMapper.builder().build());
        server.expect(requestTo("https://api.github.com/graphql"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"data":{"repository":{"defaultBranchRef":{"name":"main"},
                        "refs":{"pageInfo":{"hasNextPage":false},"nodes":[
                          {"name":"dev","target":{"oid":"aaa1111bbbb","messageHeadline":"wip","committedDate":"2026-10-13T00:00:00Z","author":{"name":"a"}}},
                          {"name":"main","target":{"oid":"bbb2222cccc","messageHeadline":"ship","committedDate":"2026-10-01T00:00:00Z","author":{"name":"a"}}}
                        ]}}}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/graphql"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"data":{"repository":{"ref":{"c0":{"aheadBy":2,"behindBy":1,"status":"DIVERGED"}}}}}
                        """, MediaType.APPLICATION_JSON));

        BranchPage page = client.branches("github", TOKEN, "leon-lv", "leon-tools");
        assertEquals("main", page.defaultBranch());
        assertEquals("main", page.branches().get(0).name());
        assertEquals("default", page.branches().get(0).merge());
        assertEquals("dev", page.branches().get(1).name());
        assertEquals("diverged", page.branches().get(1).merge());
        assertEquals(2, page.branches().get(1).ahead());
        assertEquals(1, page.branches().get(1).behind());
        server.verify();
    }

    @Test
    void githubGraphJoinsBranchHistory() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        GitHostClient client = new GitHostClient(http, JsonMapper.builder().build());
        server.expect(requestTo("https://api.github.com/graphql"))
                .andRespond(withSuccess("""
                        {"data":{"repository":{"defaultBranchRef":{"name":"main"},
                        "refs":{"pageInfo":{"hasNextPage":false},"nodes":[
                          {"name":"dev","target":{"oid":"aaa1111bbbb","messageHeadline":"wip","committedDate":"2026-10-13T00:00:00Z","author":{"name":"a"}}},
                          {"name":"main","target":{"oid":"bbb2222cccc","messageHeadline":"ship","committedDate":"2026-10-01T00:00:00Z","author":{"name":"a"}}}
                        ]}}}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/graphql"))
                .andRespond(withSuccess("""
                        {"data":{"repository":{"ref":{"c0":{"aheadBy":2,"behindBy":1,"status":"DIVERGED"}}}}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/repos/leon-lv/leon-tools/commits?sha=main&page=1&per_page=12"))
                .andRespond(withSuccess("""
                        [{"sha":"bbb2222cccc","parents":[{"sha":"ccc3333dddd"}],
                          "commit":{"message":"ship","author":{"name":"a","date":"2026-10-01T00:00:00Z"}}},
                         {"sha":"ccc3333dddd","parents":[],
                          "commit":{"message":"base","author":{"name":"a","date":"2026-09-01T00:00:00Z"}}}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/repos/leon-lv/leon-tools/commits?sha=dev&page=1&per_page=12"))
                .andRespond(withSuccess("""
                        [{"sha":"aaa1111bbbb","parents":[{"sha":"ccc3333dddd"}],
                          "commit":{"message":"wip","author":{"name":"a","date":"2026-10-13T00:00:00Z"}}}]
                        """, MediaType.APPLICATION_JSON));

        GraphPage page = client.graph("github", TOKEN, "leon-lv", "leon-tools");
        assertEquals("main", page.defaultBranch());
        assertEquals("aaa1111bbbb", page.commits().get(0).sha());
        assertEquals(List.of("dev"), page.commits().get(0).branches());
        assertEquals(List.of("ccc3333dddd"), page.commits().get(0).parents());
        assertEquals(List.of("main"), page.commits().get(1).branches());
        server.verify();
    }

    @Test
    void githubMergeConflictDoesNotEchoToken() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        GitHostClient client = new GitHostClient(http, JsonMapper.builder().build());
        server.expect(requestTo("https://api.github.com/repos/leon-lv/leon-tools/merges"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Merge conflict " + TOKEN + "\"}"));

        BizException ex = assertThrows(BizException.class,
                () -> client.merge("github", TOKEN, "leon-lv", "leon-tools", "main", "dev"));
        assertEquals("有冲突，需要在电脑上处理", ex.getMessage());
        assertFalse(ex.getMessage().contains(TOKEN));
        server.verify();
    }

    @Test
    void giteeMergeCreatesPullThenMerges() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        GitHostClient client = new GitHostClient(http, JsonMapper.builder().build());
        server.expect(requestTo("https://gitee.com/api/v5/repos/sfnss/pic/pulls"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"number\":7}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gitee.com/api/v5/repos/sfnss/pic/pulls/7/merge"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{\"merge_commit_sha\":\"abc1234def\"}", MediaType.APPLICATION_JSON));

        MergeResult result = client.merge("gitee", TOKEN, "sfnss", "pic", "master", "dev");
        assertEquals("merged", result.outcome());
        assertEquals("abc1234def", result.sha());
        assertEquals("已合并进 master", result.message());
        server.verify();
    }
}
