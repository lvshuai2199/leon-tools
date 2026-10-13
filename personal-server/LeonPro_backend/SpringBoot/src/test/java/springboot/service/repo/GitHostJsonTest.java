package springboot.service.repo;

import org.junit.jupiter.api.Test;
import springboot.DTO.RepoViews.Branch;
import springboot.DTO.RepoViews.CommitDetail;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHostJsonTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void githubGraphqlReposKeepLastCommit() throws Exception {
        GitHostJson.GithubRepos page = GitHostJson.githubRepos(json.readTree("""
                {"data":{"viewer":{"login":"leon-lv","repositories":{"pageInfo":{"hasNextPage":false,"endCursor":null},
                "nodes":[
                  {"name":"leon-tools","nameWithOwner":"leon-lv/leon-tools","isPrivate":true,"isArchived":false,
                   "description":"tools","url":"https://github.com/leon-lv/leon-tools","pushedAt":"2026-10-13T08:00:00Z",
                   "stargazerCount":2,"primaryLanguage":{"name":"Java"},"issues":{"totalCount":1},
                   "defaultBranchRef":{"name":"main","target":{"oid":"abc1234def567890","messageHeadline":"fix deploy",
                   "committedDate":"2026-10-13T07:00:00Z","author":{"name":"Shuai","user":{"login":"leon-lv"}}}}},
                  null
                ]}}}}
                """));
        assertNotNull(page);
        assertEquals("leon-lv", page.login());
        assertFalse(page.hasNext());
        assertEquals(1, page.repos().size());
        assertEquals("leon-lv/leon-tools", page.repos().get(0).fullName());
        assertTrue(page.repos().get(0).privateRepo());
        assertEquals("Java", page.repos().get(0).language());
        assertEquals(1, page.repos().get(0).openIssues());
        assertEquals("fix deploy", page.repos().get(0).lastCommit().message());
        assertEquals("leon-lv", page.repos().get(0).lastCommit().author());
        assertEquals("abc1234", page.repos().get(0).lastCommit().shortSha());
    }

    @Test
    void restRepoReadsGiteeShape() throws Exception {
        var repos = GitHostJson.restRepos("gitee", json.readTree("""
                [{"full_name":"sfnss/pic","name":"pic","private":false,"description":"pics",
                  "html_url":"https://gitee.com/sfnss/pic","default_branch":"master","language":null,
                  "pushed_at":"2026-10-12T10:00:00+08:00","stargazers_count":0,"open_issues_count":2,
                  "namespace":{"path":"sfnss"}}]
                """));
        assertEquals(1, repos.size());
        assertEquals("gitee", repos.get(0).provider());
        assertEquals("sfnss", repos.get(0).owner());
        assertEquals("master", repos.get(0).defaultBranch());
        assertFalse(repos.get(0).privateRepo());
        assertEquals(2, repos.get(0).openIssues());
        assertNull(repos.get(0).lastCommit());
    }

    @Test
    void commitDetailKeepsMessageAndClipsPatch() throws Exception {
        String patch = "x".repeat(4500);
        CommitDetail detail = GitHostJson.commitDetail(json.readTree("""
                {"sha":"abc1234def","html_url":"https://github.com/a/b/commit/abc1234def",
                 "commit":{"message":"hello\\n\\nbody","author":{"name":"A","date":"2026-10-13T00:00:00Z"}},
                 "author":{"login":"leon"},
                 "stats":{"additions":3,"deletions":1},
                 "files":[{"filename":"a.txt","status":"added","additions":3,"deletions":1,"patch":"%s"}]}
                """.formatted(patch)));
        assertNotNull(detail);
        assertEquals("hello\n\nbody", detail.message());
        assertEquals("leon", detail.author());
        assertEquals(3, detail.additions());
        assertEquals("added", detail.files().get(0).status());
        assertTrue(detail.files().get(0).patch().endsWith("…（后面已截断）"));
        assertTrue(detail.files().get(0).patch().length() < patch.length());
    }

    @Test
    void defaultBranchStaysFirst() {
        List<Branch> sorted = GitHostJson.sortBranches("main", List.of(
                new Branch("dev", "aaa", "aaa", "new", "a", "2026-10-13T00:00:00Z"),
                new Branch("main", "bbb", "bbb", "old", "a", "2026-01-01T00:00:00Z"),
                new Branch("old", "ccc", "ccc", "older", "a", "2020-01-01T00:00:00Z")));
        assertEquals(List.of("main", "dev", "old"), sorted.stream().map(Branch::name).toList());
    }

    @Test
    void httpMessageStripsToken() {
        String token = "ghp_test_token_value_123456";
        assertEquals("GitHub 令牌无效或没有仓库权限", GitHostJson.httpMessage("github", 401, "bad " + token, token));
        assertFalse(GitHostJson.httpMessage("github", 422, "nope " + token, token).contains(token));
    }
}
