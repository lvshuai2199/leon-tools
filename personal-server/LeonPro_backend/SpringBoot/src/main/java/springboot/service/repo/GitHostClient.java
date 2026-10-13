package springboot.service.repo;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import springboot.DTO.RepoViews.Branch;
import springboot.DTO.RepoViews.BranchPage;
import springboot.service.repo.GitHostJson.AheadBehind;
import springboot.DTO.RepoViews.CommitBrief;
import springboot.DTO.RepoViews.CommitDetail;
import springboot.DTO.RepoViews.CommitPage;
import springboot.DTO.RepoViews.RepoCard;
import springboot.utils.BizException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 用用户自己的令牌读 GitHub / Gitee。令牌只放在请求头里，失败信息里会抹掉。
 */
public class GitHostClient {

    private static final String REPO_QUERY = """
            query($cursor: String) {
              viewer {
                login
                repositories(first: 50, after: $cursor, orderBy: {field: PUSHED_AT, direction: DESC}) {
                  pageInfo { hasNextPage endCursor }
                  nodes {
                    name
                    nameWithOwner
                    isPrivate
                    isArchived
                    description
                    url
                    pushedAt
                    stargazerCount
                    primaryLanguage { name }
                    issues(states: OPEN) { totalCount }
                    defaultBranchRef {
                      name
                      target {
                        ... on Commit {
                          oid
                          messageHeadline
                          committedDate
                          author { name user { login } }
                        }
                      }
                    }
                  }
                }
              }
            }
            """;

    private static final String BRANCH_QUERY = """
            query($owner: String!, $name: String!) {
              repository(owner: $owner, name: $name) {
                defaultBranchRef { name }
                refs(refPrefix: "refs/heads/", first: 100, orderBy: {field: ALPHABETICAL, direction: ASC}) {
                  pageInfo { hasNextPage }
                  nodes {
                    name
                    target {
                      ... on Commit {
                        oid
                        messageHeadline
                        committedDate
                        author { name }
                      }
                    }
                  }
                }
              }
            }
            """;

    private final RestTemplate http;
    private final JsonMapper json;

    public GitHostClient(RestTemplate http, JsonMapper json) {
        this.http = http;
        this.json = json;
    }

    public static RestTemplate defaultHttp() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(8));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return new RestTemplate(factory);
    }

    public String verify(String provider, String token) {
        String url = "github".equals(provider) ? "https://api.github.com/user" : "https://gitee.com/api/v5/user";
        String login = GitHostJson.text(get(provider, token, url), "login");
        if (login.isBlank()) {
            throw new BizException(nameOf(provider) + " 没有返回账号");
        }
        return login;
    }

    public RemoteList listGithub(String token) {
        BizException graphqlError;
        try {
            return viaGraphql(token);
        } catch (BizException e) {
            if (stop(e)) {
                throw e;
            }
            graphqlError = e;
        }
        try {
            return viaGithubRest(token);
        } catch (BizException restError) {
            if (stop(restError)) {
                throw restError;
            }
            throw graphqlError;
        }
    }

    public RemoteList listGitee(String token) {
        JsonNode user = get("gitee", token, "https://gitee.com/api/v5/user");
        String login = GitHostJson.text(user, "login");
        List<RepoCard> repos = new ArrayList<>();
        for (int page = 1; page <= 2; page++) {
            String url = "https://gitee.com/api/v5/user/repos?type=all&sort=pushed&direction=desc&page="
                    + page + "&per_page=100";
            JsonNode arr = get("gitee", token, url);
            if (arr == null || !arr.isArray()) {
                throw new BizException("Gitee 没有返回仓库列表");
            }
            if (arr.isEmpty()) {
                break;
            }
            repos.addAll(GitHostJson.restRepos("gitee", arr));
            if (arr.size() < 100) {
                break;
            }
        }
        fillLatest("gitee", token, repos);
        return new RemoteList(login, repos);
    }

    public BranchPage branches(String provider, String token, String owner, String name) {
        if ("github".equals(provider)) {
            return githubBranches(token, owner, name);
        }
        return giteeBranches(token, owner, name);
    }

    public CommitPage commits(String provider, String token, String owner, String name, String ref, int page) {
        JsonNode root = get(provider, token, commitsUrl(provider, owner, name, ref, page, 30));
        if (root != null && !root.isArray()) {
            throw new BizException(nameOf(provider) + " 没有返回提交");
        }
        return new CommitPage(ref, page, GitHostJson.commitList(root));
    }

    public CommitDetail commit(String provider, String token, String owner, String name, String sha) {
        String url = "github".equals(provider)
                ? "https://api.github.com/repos/" + enc(owner) + "/" + enc(name) + "/commits/" + enc(sha)
                : "https://gitee.com/api/v5/repos/" + enc(owner) + "/" + enc(name) + "/commits/" + enc(sha);
        CommitDetail detail = GitHostJson.commitDetail(get(provider, token, url));
        if (detail == null) {
            throw new BizException("提交不存在，或当前令牌看不到它");
        }
        return detail;
    }

    private RemoteList viaGraphql(String token) {
        String cursor = null;
        String login = "";
        List<RepoCard> repos = new ArrayList<>();
        for (int page = 0; page < 4; page++) {
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("cursor", cursor);
            JsonNode root = graphql(token, REPO_QUERY, vars);
            GitHostJson.GithubRepos parsed = GitHostJson.githubRepos(root);
            if (parsed == null) {
                throw new BizException("GitHub 没有返回仓库列表");
            }
            if (login.isBlank()) {
                login = parsed.login();
            }
            repos.addAll(parsed.repos());
            if (!parsed.hasNext() || parsed.cursor() == null || parsed.cursor().isBlank()
                    || parsed.cursor().equals(cursor)) {
                break;
            }
            cursor = parsed.cursor();
        }
        return new RemoteList(login, repos);
    }

    private RemoteList viaGithubRest(String token) {
        String login = GitHostJson.text(get("github", token, "https://api.github.com/user"), "login");
        List<RepoCard> repos = new ArrayList<>();
        for (int page = 1; page <= 2; page++) {
            String url = "https://api.github.com/user/repos?per_page=100&sort=pushed&direction=desc"
                    + "&affiliation=owner,collaborator,organization_member&page=" + page;
            JsonNode arr = get("github", token, url);
            if (arr == null || !arr.isArray()) {
                throw new BizException("GitHub 没有返回仓库列表");
            }
            if (arr.isEmpty()) {
                break;
            }
            repos.addAll(GitHostJson.restRepos("github", arr));
            if (arr.size() < 100) {
                break;
            }
        }
        fillLatest("github", token, repos);
        return new RemoteList(login, repos);
    }

    private BranchPage githubBranches(String token, String owner, String name) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("owner", owner);
        vars.put("name", name);
        JsonNode root = graphql(token, BRANCH_QUERY, vars);
        List<Branch> branches = GitHostJson.githubBranches(root);
        if (branches == null) {
            throw new BizException("不存在，或当前令牌看不到它");
        }
        String def = GitHostJson.githubDefaultBranch(root);
        branches = GitHostJson.applyMerges(def, branches, githubAhead(token, owner, name, def, branches));
        return new BranchPage(def, GitHostJson.githubBranchesTruncated(root), GitHostJson.sortBranches(def, branches));
    }

    /**
     * 默认分支当 base，其它分支当 head。aheadBy 就是还没进默认分支的提交。
     * 一次比太多会顶到 GraphQL 复杂度，按 25 条拆开。比失败就留下已经比出来的。
     */
    private Map<String, AheadBehind> githubAhead(String token, String owner, String name, String base, List<Branch> branches) {
        Map<String, AheadBehind> out = new LinkedHashMap<>();
        if (base == null || base.isBlank()) {
            return out;
        }
        String baseSha = "";
        for (Branch branch : branches) {
            if (base.equals(branch.name()) && branch.sha() != null) {
                baseSha = branch.sha();
                break;
            }
        }
        List<String> heads = new ArrayList<>();
        for (Branch branch : branches) {
            if (branch.name() == null || branch.name().isBlank() || base.equals(branch.name())) {
                continue;
            }
            if (!baseSha.isBlank() && baseSha.equals(branch.sha())) {
                continue;
            }
            heads.add(branch.name());
        }
        for (int from = 0; from < heads.size(); from += 25) {
            List<String> chunk = heads.subList(from, Math.min(heads.size(), from + 25));
            try {
                JsonNode ref = graphql(token, compareQuery(chunk.size()), compareVars(owner, name, base, chunk))
                        .path("data").path("repository").path("ref");
                if (ref.isMissingNode() || ref.isNull()) {
                    break;
                }
                for (int i = 0; i < chunk.size(); i++) {
                    AheadBehind cmp = GitHostJson.readCompare(ref.get("c" + i));
                    if (cmp != null) {
                        out.put(chunk.get(i), cmp);
                    }
                }
            } catch (RuntimeException e) {
                break;
            }
        }
        return out;
    }

    private static String compareQuery(int n) {
        StringBuilder q = new StringBuilder();
        q.append("query($owner: String!, $name: String!, $base: String!");
        for (int i = 0; i < n; i++) {
            q.append(", $h").append(i).append(": String!");
        }
        q.append(") { repository(owner: $owner, name: $name) { ref(qualifiedName: $base) {");
        for (int i = 0; i < n; i++) {
            q.append(" c").append(i).append(": compare(headRef: $h").append(i).append(") { aheadBy behindBy status }");
        }
        q.append(" } } }");
        return q.toString();
    }

    private static Map<String, Object> compareVars(String owner, String name, String base, List<String> heads) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("owner", owner);
        vars.put("name", name);
        vars.put("base", "refs/heads/" + base);
        for (int i = 0; i < heads.size(); i++) {
            vars.put("h" + i, heads.get(i));
        }
        return vars;
    }

    private BranchPage giteeBranches(String token, String owner, String name) {
        JsonNode repo = get("gitee", token, "https://gitee.com/api/v5/repos/" + enc(owner) + "/" + enc(name));
        String def = GitHostJson.text(repo, "default_branch");
        JsonNode arr = get("gitee", token,
                "https://gitee.com/api/v5/repos/" + enc(owner) + "/" + enc(name) + "/branches?page=1&per_page=100");
        if (arr != null && !arr.isArray()) {
            throw new BizException("Gitee 没有返回分支");
        }
        List<Branch> branches = new ArrayList<>();
        if (arr != null) {
            for (JsonNode item : arr) {
                if (item == null || item.isNull()) {
                    continue;
                }
                String branch = GitHostJson.text(item, "name");
                if (branch.isBlank()) {
                    continue;
                }
                String sha = GitHostJson.text(item.path("commit"), "sha");
                branches.add(new Branch(branch, sha, GitHostJson.shortSha(sha), "", "", "", "", 0, 0));
            }
        }
        fillBranchTips(token, owner, name, def, branches);
        return new BranchPage(def, branches.size() >= 100, GitHostJson.sortBranches(def, branches));
    }

    private void fillLatest(String provider, String token, List<RepoCard> repos) {
        int n = Math.min(repos.size(), 40);
        CommitBrief[] found = new CommitBrief[n];
        List<Runnable> jobs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            RepoCard repo = repos.get(i);
            if (repo.lastCommit() != null || repo.defaultBranch() == null || repo.defaultBranch().isBlank()) {
                continue;
            }
            int idx = i;
            jobs.add(() -> found[idx] = firstCommit(provider, token, repo.owner(), repo.name(), repo.defaultBranch()));
        }
        parallel(jobs, Duration.ofSeconds(8));
        for (int i = 0; i < n; i++) {
            if (found[i] != null) {
                repos.set(i, withLast(repos.get(i), found[i]));
            }
        }
    }

    private void fillBranchTips(String token, String owner, String name, String def, List<Branch> branches) {
        String defSha = "";
        for (Branch branch : branches) {
            if (def != null && def.equals(branch.name())) {
                defSha = branch.sha() == null ? "" : branch.sha();
                break;
            }
        }
        int n = Math.min(branches.size(), 40);
        CommitBrief[] found = new CommitBrief[n];
        AheadBehind[] merges = new AheadBehind[n];
        List<Runnable> jobs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Branch branch = branches.get(i);
            if (branch.name() == null || branch.name().isBlank()) {
                continue;
            }
            int idx = i;
            String head = branch.name();
            String sha = branch.sha() == null ? "" : branch.sha();
            boolean skipCompare = def == null || def.isBlank() || def.equals(head) || (!defSha.isBlank() && defSha.equals(sha));
            jobs.add(() -> {
                found[idx] = firstCommit("gitee", token, owner, name, head);
                if (!skipCompare) {
                    merges[idx] = giteeCompare(token, owner, name, def, head);
                }
            });
        }
        parallel(jobs, Duration.ofSeconds(12));
        for (int i = 0; i < n; i++) {
            CommitBrief brief = found[i];
            if (brief == null) {
                continue;
            }
            Branch old = branches.get(i);
            branches.set(i, new Branch(old.name(), brief.sha().isBlank() ? old.sha() : brief.sha(),
                    brief.shortSha().isBlank() ? old.shortSha() : brief.shortSha(),
                    brief.message(), brief.author(), brief.date(), "", 0, 0));
        }
        List<Branch> annotated = GitHostJson.applyMerges(def, branches, mergeMap(branches, merges));
        branches.clear();
        branches.addAll(annotated);
    }

    private static Map<String, AheadBehind> mergeMap(List<Branch> branches, AheadBehind[] merges) {
        Map<String, AheadBehind> out = new LinkedHashMap<>();
        int n = Math.min(branches.size(), merges.length);
        for (int i = 0; i < n; i++) {
            if (merges[i] != null && branches.get(i).name() != null) {
                out.put(branches.get(i).name(), merges[i]);
            }
        }
        return out;
    }

    private AheadBehind giteeCompare(String token, String owner, String name, String base, String head) {
        try {
            String url = "https://gitee.com/api/v5/repos/" + enc(owner) + "/" + enc(name)
                    + "/compare/" + enc(base) + "..." + enc(head);
            return GitHostJson.readCompare(get("gitee", token, url));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private CommitBrief firstCommit(String provider, String token, String owner, String name, String ref) {
        try {
            List<CommitBrief> list = GitHostJson.commitList(get(provider, token, commitsUrl(provider, owner, name, ref, 1, 1)));
            return list.isEmpty() ? null : list.get(0);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void parallel(List<Runnable> jobs, Duration budget) {
        if (jobs.isEmpty()) {
            return;
        }
        ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
        Semaphore gate = new Semaphore(6);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable job : jobs) {
                futures.add(exec.submit(() -> {
                    try {
                        if (!gate.tryAcquire(budget.toMillis(), TimeUnit.MILLISECONDS)) {
                            return null;
                        }
                        try {
                            job.run();
                        } finally {
                            gate.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }));
            }
            long deadline = System.nanoTime() + budget.toNanos();
            for (Future<?> future : futures) {
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    future.cancel(true);
                    continue;
                }
                try {
                    future.get(left, TimeUnit.NANOSECONDS);
                } catch (Exception e) {
                    future.cancel(true);
                }
            }
        } finally {
            exec.shutdownNow();
        }
    }

    private JsonNode graphql(String token, String query, Map<String, Object> variables) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("variables", variables);
        JsonNode root = post("github", token, "https://api.github.com/graphql", body);
        JsonNode data = root == null ? null : root.get("data");
        if (data == null || data.isNull()) {
            String remote = "";
            JsonNode errors = root == null ? null : root.get("errors");
            if (errors != null && errors.isArray() && !errors.isEmpty()) {
                remote = GitHostJson.text(errors.get(0), "message");
            }
            throw new BizException(GitHostJson.httpMessage("github", 200, remote, token));
        }
        return root;
    }

    private static String commitsUrl(String provider, String owner, String name, String ref, int page, int perPage) {
        String query = "sha=" + enc(ref) + "&page=" + page + "&per_page=" + perPage;
        if ("github".equals(provider)) {
            return "https://api.github.com/repos/" + enc(owner) + "/" + enc(name) + "/commits?" + query;
        }
        return "https://gitee.com/api/v5/repos/" + enc(owner) + "/" + enc(name) + "/commits?" + query;
    }

    private JsonNode get(String provider, String token, String url) {
        return exchange(provider, token, HttpMethod.GET, url, null);
    }

    private JsonNode post(String provider, String token, String url, Object body) {
        return exchange(provider, token, HttpMethod.POST, url, body);
    }

    private JsonNode exchange(String provider, String token, HttpMethod method, String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "LeonPro");
        if ("github".equals(provider)) {
            headers.setBearerAuth(token);
            headers.set("X-GitHub-Api-Version", "2022-11-28");
        } else {
            headers.set(HttpHeaders.AUTHORIZATION, "token " + token);
        }
        String payload = null;
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            payload = json.writeValueAsString(body);
        }
        try {
            String raw = http.exchange(URI.create(url), method, new HttpEntity<>(payload, headers), String.class).getBody();
            return read(raw, provider);
        } catch (HttpStatusCodeException e) {
            throw new BizException(GitHostJson.httpMessage(provider, e.getStatusCode().value(), remoteMessage(e.getResponseBodyAsString()), token));
        } catch (ResourceAccessException e) {
            throw new BizException(nameOf(provider) + " 连接超时，请稍后再试");
        }
    }

    private JsonNode read(String raw, String provider) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.charAt(0) == '<') {
            throw new BizException(nameOf(provider) + " 返回了网页，请稍后再试");
        }
        try {
            return json.readTree(raw);
        } catch (Exception e) {
            throw new BizException(nameOf(provider) + " 返回的数据无法解析");
        }
    }

    private String remoteMessage(String raw) {
        if (raw == null || raw.isBlank() || raw.charAt(0) == '<') {
            return "";
        }
        try {
            JsonNode node = json.readTree(raw);
            String msg = GitHostJson.text(node, "message");
            if (!msg.isBlank()) {
                return msg;
            }
            JsonNode errors = node.get("errors");
            if (errors != null && errors.isArray() && !errors.isEmpty()) {
                return GitHostJson.text(errors.get(0), "message");
            }
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    private static boolean stop(BizException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return msg.contains("令牌无效") || msg.contains("连接超时") || msg.contains("请求太频繁");
    }

    private static RepoCard withLast(RepoCard card, CommitBrief last) {
        return new RepoCard(card.provider(), card.owner(), card.name(), card.fullName(), card.privateRepo(),
                card.archived(), card.description(), card.defaultBranch(), card.language(), card.htmlUrl(),
                card.pushedAt(), card.stars(), card.openIssues(), last);
    }

    private static String nameOf(String provider) {
        return "gitee".equals(provider) ? "Gitee" : "GitHub";
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public record RemoteList(String login, List<RepoCard> repos) {
    }
}
