package springboot.service.repo;

import springboot.DTO.RepoViews.Branch;
import springboot.DTO.RepoViews.CommitBrief;
import springboot.DTO.RepoViews.CommitDetail;
import springboot.DTO.RepoViews.FileChange;
import springboot.DTO.RepoViews.RepoCard;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 把 GitHub / Gitee 的 JSON 收成手机端要用的字段。不发网络请求。 */
public final class GitHostJson {

    private static final int PATCH_LIMIT = 4000;
    private static final int FILE_LIMIT = 40;

    private GitHostJson() {
    }

    public record GithubRepos(String login, boolean hasNext, String cursor, List<RepoCard> repos) {
    }

    public static GithubRepos githubRepos(JsonNode root) {
        JsonNode viewer = root == null ? null : root.path("data").path("viewer");
        if (viewer == null || viewer.isMissingNode() || viewer.isNull()) {
            return null;
        }
        JsonNode conn = viewer.path("repositories");
        JsonNode page = conn.path("pageInfo");
        boolean hasNext = truth(page.get("hasNextPage"));
        String cursor = text(page, "endCursor");
        List<RepoCard> repos = new ArrayList<>();
        JsonNode nodes = conn.get("nodes");
        if (nodes != null && nodes.isArray()) {
            for (JsonNode node : nodes) {
                RepoCard card = graphqlRepo(node);
                if (card != null) {
                    repos.add(card);
                }
            }
        }
        return new GithubRepos(text(viewer, "login"), hasNext, cursor, repos);
    }

    public static List<RepoCard> restRepos(String provider, JsonNode arr) {
        List<RepoCard> repos = new ArrayList<>();
        if (arr == null || !arr.isArray()) {
            return repos;
        }
        for (JsonNode node : arr) {
            RepoCard card = restRepo(provider, node);
            if (card != null) {
                repos.add(card);
            }
        }
        return repos;
    }

    public static List<Branch> githubBranches(JsonNode root) {
        JsonNode repo = root == null ? null : root.path("data").path("repository");
        if (repo == null || repo.isMissingNode() || repo.isNull()) {
            return null;
        }
        List<Branch> branches = new ArrayList<>();
        JsonNode nodes = repo.path("refs").get("nodes");
        if (nodes != null && nodes.isArray()) {
            for (JsonNode node : nodes) {
                if (node == null || node.isNull()) {
                    continue;
                }
                String name = text(node, "name");
                if (name.isBlank()) {
                    continue;
                }
                JsonNode target = node.get("target");
                String sha = text(target, "oid");
                branches.add(new Branch(name, sha, shortSha(sha), headline(text(target, "messageHeadline")),
                        authorOf(target), text(target, "committedDate")));
            }
        }
        return branches;
    }

    public static boolean githubBranchesTruncated(JsonNode root) {
        JsonNode page = root == null ? null : root.path("data").path("repository").path("refs").path("pageInfo");
        return page != null && truth(page.get("hasNextPage"));
    }

    public static String githubDefaultBranch(JsonNode root) {
        JsonNode repo = root == null ? null : root.path("data").path("repository");
        if (repo == null || repo.isMissingNode() || repo.isNull()) {
            return "";
        }
        return text(repo.path("defaultBranchRef"), "name");
    }

    public static List<CommitBrief> commitList(JsonNode root) {
        List<CommitBrief> list = new ArrayList<>();
        if (root == null || !root.isArray()) {
            return list;
        }
        for (JsonNode item : root) {
            CommitBrief brief = briefOf(item);
            if (brief != null) {
                list.add(brief);
            }
        }
        return list;
    }

    public static CommitDetail commitDetail(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return null;
        }
        CommitBrief brief = briefOf(node);
        if (brief == null) {
            return null;
        }
        String message = text(node.path("commit"), "message");
        if (message.isBlank()) {
            message = text(node, "message");
        }
        message = message.trim();
        if (message.length() > 8000) {
            message = message.substring(0, 8000);
        }
        JsonNode files = node.get("files");
        List<FileChange> changes = new ArrayList<>();
        boolean truncated = false;
        int add = 0;
        int del = 0;
        if (files != null && files.isArray()) {
            truncated = files.size() > FILE_LIMIT;
            int n = Math.min(files.size(), FILE_LIMIT);
            for (int i = 0; i < n; i++) {
                JsonNode file = files.get(i);
                String filename = text(file, "filename");
                if (filename.isBlank()) {
                    continue;
                }
                int fa = integer(file, "additions");
                int fd = integer(file, "deletions");
                add += fa;
                del += fd;
                String status = text(file, "status");
                if (status.isBlank()) {
                    status = "modified";
                }
                changes.add(new FileChange(filename, status, fa, fd, clipPatch(text(file, "patch"))));
            }
        }
        JsonNode stats = node.get("stats");
        if (stats != null && !stats.isNull() && (stats.get("additions") != null || stats.get("deletions") != null)) {
            add = integer(stats, "additions");
            del = integer(stats, "deletions");
        }
        return new CommitDetail(brief.sha(), brief.shortSha(), message.isBlank() ? brief.message() : message,
                brief.author(), brief.date(), brief.htmlUrl(), add, del, truncated, changes);
    }

    public static List<Branch> sortBranches(String defaultBranch, List<Branch> raw) {
        List<Branch> copy = new ArrayList<>(raw);
        String def = defaultBranch == null ? "" : defaultBranch;
        copy.sort((a, b) -> {
            boolean ad = def.equals(a.name());
            boolean bd = def.equals(b.name());
            if (ad != bd) {
                return ad ? -1 : 1;
            }
            return instant(b.date()).compareTo(instant(a.date()));
        });
        return copy;
    }

    public static String httpMessage(String provider, int status, String remote, String token) {
        String name = "gitee".equals(provider) ? "Gitee" : "GitHub";
        if (status == 401 || status == 403) {
            return name + " 令牌无效或没有仓库权限";
        }
        if (status == 404) {
            return "不存在，或当前令牌看不到它";
        }
        if (status == 429) {
            return name + " 请求太频繁，请稍后再试";
        }
        String msg = remote == null ? "" : remote;
        if (token != null && !token.isBlank()) {
            msg = msg.replace(token, "***");
        }
        msg = msg.replace('\n', ' ').trim();
        if (msg.length() > 160) {
            msg = msg.substring(0, 160);
        }
        if (!msg.isBlank() && !msg.contains("://") && !msg.toLowerCase(Locale.ROOT).contains("bearer")) {
            return name + "：" + msg;
        }
        if (status == 200) {
            return name + " 没有返回数据";
        }
        return name + " 接口失败（" + status + "）";
    }

    public static String shortSha(String sha) {
        if (sha == null || sha.isBlank()) {
            return "";
        }
        return sha.length() <= 7 ? sha : sha.substring(0, 7);
    }

    static Instant instant(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception ignored) {
            // 有的接口不带时区
        }
        try {
            return LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return Instant.EPOCH;
        }
    }

    static String text(JsonNode node, String field) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        String s = value.asString();
        return s == null ? "" : s;
    }

    private static RepoCard graphqlRepo(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String name = text(node, "name");
        String full = text(node, "nameWithOwner");
        String owner = "";
        int slash = full.lastIndexOf('/');
        if (slash > 0) {
            owner = full.substring(0, slash);
            if (name.isBlank()) {
                name = full.substring(slash + 1);
            }
        }
        if (name.isBlank()) {
            return null;
        }
        if (full.isBlank()) {
            full = owner.isBlank() ? name : owner + "/" + name;
        }
        JsonNode branch = node.get("defaultBranchRef");
        String branchName = text(branch, "name");
        JsonNode target = branch == null ? null : branch.get("target");
        String sha = text(target, "oid");
        CommitBrief last = sha.isBlank() ? null : new CommitBrief(sha, shortSha(sha),
                headline(text(target, "messageHeadline")), authorOf(target), text(target, "committedDate"), "");
        return new RepoCard("github", owner, name, full, flag(node, "isPrivate"), flag(node, "isArchived"),
                clip(text(node, "description"), 300), branchName, text(node.path("primaryLanguage"), "name"),
                text(node, "url"), text(node, "pushedAt"), integer(node, "stargazerCount"),
                integer(node.path("issues"), "totalCount"), last);
    }

    private static RepoCard restRepo(String provider, JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String name = text(node, "name");
        String full = text(node, "full_name");
        String owner = text(node.path("namespace"), "path");
        if (owner.isBlank()) {
            owner = text(node.path("owner"), "login");
        }
        if (owner.isBlank() && full.contains("/")) {
            owner = full.substring(0, full.indexOf('/'));
        }
        if (name.isBlank() && full.contains("/")) {
            name = full.substring(full.indexOf('/') + 1);
        }
        if (name.isBlank()) {
            return null;
        }
        if (full.isBlank()) {
            full = owner.isBlank() ? name : owner + "/" + name;
        }
        boolean priv = node.get("private") != null && !node.get("private").isNull()
                ? flag(node, "private")
                : node.get("public") != null && !flag(node, "public");
        return new RepoCard(provider, owner, name, full, priv, flag(node, "archived"),
                clip(text(node, "description"), 300), text(node, "default_branch"), text(node, "language"),
                text(node, "html_url"), text(node, "pushed_at"), integer(node, "stargazers_count"),
                integer(node, "open_issues_count"), null);
    }

    private static CommitBrief briefOf(JsonNode item) {
        if (item == null || item.isNull()) {
            return null;
        }
        String sha = text(item, "sha");
        if (sha.isBlank()) {
            sha = text(item, "oid");
        }
        if (sha.isBlank()) {
            return null;
        }
        JsonNode commit = item.get("commit");
        String message = text(commit, "message");
        if (message.isBlank()) {
            message = text(item, "message");
        }
        if (message.isBlank()) {
            message = text(item, "messageHeadline");
        }
        String date = text(commit == null ? null : commit.path("author"), "date");
        if (date.isBlank()) {
            date = text(commit == null ? null : commit.path("committer"), "date");
        }
        if (date.isBlank()) {
            date = text(item, "committedDate");
        }
        String author = text(item.path("author"), "login");
        if (author.isBlank()) {
            author = authorOf(item);
        }
        if (author.isBlank()) {
            author = text(commit == null ? null : commit.path("author"), "name");
        }
        String url = text(item, "html_url");
        if (url.isBlank()) {
            url = text(item, "url");
            if (url.contains("api.github.com") || url.contains("api/v5")) {
                url = "";
            }
        }
        return new CommitBrief(sha, shortSha(sha), headline(message), author, date, url);
    }

    private static String authorOf(JsonNode commitish) {
        if (commitish == null || commitish.isNull() || commitish.isMissingNode()) {
            return "";
        }
        JsonNode author = commitish.get("author");
        if (author == null || author.isNull()) {
            return "";
        }
        String login = text(author.path("user"), "login");
        if (!login.isBlank()) {
            return login;
        }
        login = text(author, "login");
        if (!login.isBlank()) {
            return login;
        }
        return text(author, "name");
    }

    private static String headline(String message) {
        if (message == null) {
            return "";
        }
        int n = message.indexOf('\n');
        String line = (n >= 0 ? message.substring(0, n) : message).trim();
        return clip(line, 200);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String s = value.trim();
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String clipPatch(String patch) {
        if (patch == null || patch.isBlank()) {
            return "";
        }
        if (patch.length() <= PATCH_LIMIT) {
            return patch;
        }
        return patch.substring(0, PATCH_LIMIT) + "\n…（后面已截断）";
    }

    private static boolean flag(JsonNode node, String field) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return false;
        }
        return truth(node.get(field));
    }

    private static boolean truth(JsonNode value) {
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        return "true".equalsIgnoreCase(value.asString());
    }

    private static int integer(JsonNode node, String field) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return 0;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return 0;
        }
        if (value.isNumber()) {
            return value.intValue();
        }
        try {
            String s = value.asString();
            return s == null || s.isBlank() ? 0 : Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
