package springboot.service.repo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import springboot.DTO.RepoViews.Board;
import springboot.DTO.RepoViews.BranchPage;
import springboot.DTO.RepoViews.CommitDetail;
import springboot.DTO.RepoViews.CommitPage;
import springboot.DTO.RepoViews.GraphPage;
import springboot.DTO.RepoViews.Host;
import springboot.DTO.RepoViews.MergeResult;
import springboot.DTO.RepoViews.RepoCard;
import springboot.domain.SysUsers;
import springboot.service.RegCodeAccessService;
import springboot.service.cursor.CursorKeyCipher;
import springboot.service.repo.GitHostClient.RemoteList;
import springboot.utils.BizException;
import springboot.utils.ForbiddenException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 每个登录用户用自己的 GitHub / Gitee 令牌看仓库。令牌加密方式与 Cursor Key 相同，依赖 CURSOR_KEY_SECRET。
 */
@Service
public class RepoWatchService {

    /** 列表、提交图、分支提交都先用缓存。超过半天再标成过期，页面先显示旧的，拉到新的再换上。 */
    private static final long HALF_DAY_MS = 12 * 60 * 60 * 1000L;

    private final JdbcTemplate jdbc;
    private final CursorKeyCipher cipher;
    private final GitHostClient client;
    private final RegCodeAccessService access;
    private final ConcurrentHashMap<String, Timed<Board>> boardCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timed<GraphPage>> graphCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timed<CommitPage>> commitCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public RepoWatchService(JdbcTemplate jdbc, CursorKeyCipher cipher, GitHostClient client, RegCodeAccessService access) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.client = client;
        this.access = access;
    }

    public SysUsers requireUser(HttpServletRequest request) {
        SysUsers user = access.currentUser(request);
        if (user == null) {
            throw new ForbiddenException("请先登录");
        }
        if (access.isBottomSubUser(user)) {
            throw new ForbiddenException("无权使用仓库");
        }
        if (access.isRootUser(user)) {
            return user;
        }
        SysUsers governing = access.appMenuGoverningUser(user);
        if (governing == null) {
            throw new ForbiddenException("无权使用仓库");
        }
        if (access.isRootUser(governing)) {
            return user;
        }
        String menuId = reposMenuId();
        if (menuId == null || !access.hasMenu(governing, menuId)) {
            throw new ForbiddenException("无权使用仓库");
        }
        return user;
    }

    public Board board(String userId, boolean refresh) {
        return load(boardCache, userId, refresh, () -> fetchBoard(userId), RepoWatchService::freshBoard, RepoWatchService::staleBoard);
    }

    public Board saveToken(String userId, String provider, String token) {
        String p = arg(provider, RepoRefs::provider);
        String trimmed = token == null ? "" : token.trim();
        if (trimmed.length() < 8 || trimmed.length() > 400 || trimmed.chars().anyMatch(ch -> ch < 33 || ch > 126)) {
            throw new BizException("令牌格式不对");
        }
        if (!cipher.ready()) {
            throw new BizException("服务器还没配置 CURSOR_KEY_SECRET，暂时不能保存令牌");
        }
        client.verify(p, trimmed);
        String hint = trimmed.substring(trimmed.length() - 4);
        String packed;
        try {
            packed = cipher.seal(trimmed);
        } catch (IllegalStateException e) {
            throw new BizException("服务器还没配置 CURSOR_KEY_SECRET，暂时不能保存令牌");
        }
        jdbc.update(
                "INSERT INTO repo_user_token (user_id, provider, token_cipher, token_hint, update_time) VALUES (?, ?, ?, ?, NOW()) "
                        + "ON DUPLICATE KEY UPDATE token_cipher = VALUES(token_cipher), token_hint = VALUES(token_hint), update_time = NOW()",
                userId, p, packed, hint);
        dropUser(userId);
        return board(userId, true);
    }

    public Board clearToken(String userId, String provider) {
        String p = arg(provider, RepoRefs::provider);
        jdbc.update("DELETE FROM repo_user_token WHERE user_id = ? AND provider = ?", userId, p);
        dropUser(userId);
        return board(userId, true);
    }

    public BranchPage branches(String userId, String provider, String owner, String name) {
        String p = arg(provider, RepoRefs::provider);
        String o = arg(owner, v -> RepoRefs.slug(v, "所有者"));
        String n = arg(name, v -> RepoRefs.slug(v, "仓库名"));
        return client.branches(p, requireToken(userId, p), o, n);
    }

    public CommitPage commits(String userId, String provider, String owner, String name, String ref, int page, boolean refresh) {
        String p = arg(provider, RepoRefs::provider);
        String o = arg(owner, v -> RepoRefs.slug(v, "所有者"));
        String n = arg(name, v -> RepoRefs.slug(v, "仓库名"));
        String branch = arg(ref, RepoRefs::ref);
        int pageNo = RepoRefs.page(page);
        String key = userId + "|" + p + "|" + o + "|" + n + "|" + branch + "|" + pageNo;
        return load(commitCache, key, refresh,
                () -> client.commits(p, requireToken(userId, p), o, n, branch, pageNo),
                RepoWatchService::freshCommits, RepoWatchService::staleCommits);
    }

    public CommitDetail commit(String userId, String provider, String owner, String name, String sha) {
        String p = arg(provider, RepoRefs::provider);
        String o = arg(owner, v -> RepoRefs.slug(v, "所有者"));
        String n = arg(name, v -> RepoRefs.slug(v, "仓库名"));
        String id = arg(sha, RepoRefs::sha);
        return client.commit(p, requireToken(userId, p), o, n, id);
    }

    public GraphPage graph(String userId, String provider, String owner, String name, boolean refresh) {
        String p = arg(provider, RepoRefs::provider);
        String o = arg(owner, v -> RepoRefs.slug(v, "所有者"));
        String n = arg(name, v -> RepoRefs.slug(v, "仓库名"));
        String key = userId + "|" + p + "|" + o + "|" + n;
        return load(graphCache, key, refresh,
                () -> client.graph(p, requireToken(userId, p), o, n),
                RepoWatchService::freshGraph, RepoWatchService::staleGraph);
    }

    public MergeResult merge(String userId, String provider, String owner, String name, String base, String head) {
        String p = arg(provider, RepoRefs::provider);
        String o = arg(owner, v -> RepoRefs.slug(v, "所有者"));
        String n = arg(name, v -> RepoRefs.slug(v, "仓库名"));
        String b = arg(base, RepoRefs::ref);
        String h = arg(head, RepoRefs::ref);
        MergeResult result = client.merge(p, requireToken(userId, p), o, n, b, h);
        String prefix = userId + "|" + p + "|" + o + "|" + n;
        graphCache.remove(prefix);
        commitCache.keySet().removeIf(key -> key.startsWith(prefix + "|"));
        boardCache.remove(userId);
        return result;
    }

    private Board fetchBoard(String userId) {
        ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<Slice> github = exec.submit(() -> slice(userId, "github"));
            Future<Slice> gitee = exec.submit(() -> slice(userId, "gitee"));
            Slice left = await(github);
            Slice right = await(gitee);
            List<RepoCard> repos = new ArrayList<>();
            repos.addAll(left.repos);
            repos.addAll(right.repos);
            repos.sort(Comparator.comparing((RepoCard card) -> GitHostJson.instant(card.pushedAt())).reversed());
            return new Board(left.host, right.host, repos, false);
        } finally {
            exec.shutdownNow();
        }
    }

    private <T> T load(ConcurrentHashMap<String, Timed<T>> cache, String key, boolean refresh,
                       Supplier<T> fetch, java.util.function.UnaryOperator<T> fresh, java.util.function.UnaryOperator<T> stale) {
        Object lock = locks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            Timed<T> hit = cache.get(key);
            long now = System.currentTimeMillis();
            if (!refresh && hit != null) {
                return now - hit.at < HALF_DAY_MS ? fresh.apply(hit.value) : stale.apply(hit.value);
            }
            try {
                T value = fresh.apply(fetch.get());
                cache.put(key, new Timed<>(now, value));
                return value;
            } catch (RuntimeException e) {
                if (hit != null && !refresh) {
                    return stale.apply(hit.value);
                }
                throw e;
            }
        }
    }

    private void dropUser(String userId) {
        boardCache.remove(userId);
        String prefix = userId + "|";
        graphCache.keySet().removeIf(key -> key.startsWith(prefix));
        commitCache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static Board freshBoard(Board board) {
        return board.stale() ? new Board(board.github(), board.gitee(), board.repos(), false) : board;
    }

    private static Board staleBoard(Board board) {
        return board.stale() ? board : new Board(board.github(), board.gitee(), board.repos(), true);
    }

    private static GraphPage freshGraph(GraphPage page) {
        return page.stale() ? new GraphPage(page.defaultBranch(), page.truncated(), page.branches(), page.commits(), false) : page;
    }

    private static GraphPage staleGraph(GraphPage page) {
        return page.stale() ? page : new GraphPage(page.defaultBranch(), page.truncated(), page.branches(), page.commits(), true);
    }

    private static CommitPage freshCommits(CommitPage page) {
        return page.stale() ? new CommitPage(page.ref(), page.page(), page.commits(), false) : page;
    }

    private static CommitPage staleCommits(CommitPage page) {
        return page.stale() ? page : new CommitPage(page.ref(), page.page(), page.commits(), true);
    }

    private Slice slice(String userId, String provider) {
        TokenRow row = find(userId, provider);
        if (row == null) {
            return Slice.empty();
        }
        String token;
        try {
            token = cipher.open(row.cipher);
        } catch (IllegalStateException e) {
            String warning = cipher.ready() ? "保存的令牌无法读取，请重新填写" : "服务器还没配置 CURSOR_KEY_SECRET，暂时不能读取已保存的令牌";
            return new Slice(new Host(true, row.hint, "", warning), List.of());
        }
        try {
            RemoteList list = "github".equals(provider) ? client.listGithub(token) : client.listGitee(token);
            return new Slice(new Host(true, row.hint, list.login() == null ? "" : list.login(), ""), list.repos());
        } catch (BizException e) {
            return new Slice(new Host(true, row.hint, "", e.getMessage()), List.of());
        } catch (RuntimeException e) {
            return new Slice(new Host(true, row.hint, "", "读取仓库失败"), List.of());
        }
    }

    private String requireToken(String userId, String provider) {
        TokenRow row = find(userId, provider);
        if (row == null) {
            throw new BizException("先保存 " + ("gitee".equals(provider) ? "Gitee" : "GitHub") + " 令牌");
        }
        try {
            return cipher.open(row.cipher);
        } catch (IllegalStateException e) {
            if (!cipher.ready()) {
                throw new BizException("服务器还没配置 CURSOR_KEY_SECRET，暂时不能读取已保存的令牌");
            }
            throw new BizException("保存的令牌无法读取，请重新填写");
        }
    }

    private TokenRow find(String userId, String provider) {
        List<TokenRow> rows = jdbc.query(
                "SELECT token_cipher, token_hint FROM repo_user_token WHERE user_id = ? AND provider = ?",
                (rs, i) -> new TokenRow(rs.getString("token_cipher"), rs.getString("token_hint")),
                userId, provider);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String reposMenuId() {
        List<String> ids = jdbc.queryForList(
                "SELECT id FROM sys_menus WHERE client = 'app' AND disabled = 0 AND LOWER(route_key) = '/repos' LIMIT 1",
                String.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private static Slice await(Future<Slice> future) {
        try {
            return future.get(25, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return Slice.failed("读取超时，请稍后再试");
        } catch (Exception e) {
            return Slice.failed("读取仓库失败");
        }
    }

    private static <T> T arg(String raw, java.util.function.Function<String, T> parse) {
        try {
            return parse.apply(raw);
        } catch (IllegalArgumentException e) {
            throw new BizException(e.getMessage());
        }
    }

    private record TokenRow(String cipher, String hint) {
    }

    private record Timed<T>(long at, T value) {
    }

    private static final class Slice {
        private final Host host;
        private final List<RepoCard> repos;

        private Slice(Host host, List<RepoCard> repos) {
            this.host = host;
            this.repos = repos;
        }

        private static Slice empty() {
            return new Slice(new Host(false, "", "", ""), List.of());
        }

        private static Slice failed(String warning) {
            return new Slice(new Host(true, "", "", warning), List.of());
        }
    }
}
