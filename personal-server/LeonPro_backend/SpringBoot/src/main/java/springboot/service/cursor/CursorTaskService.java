package springboot.service.cursor;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import springboot.DTO.CursorViews.Board;
import springboot.DTO.CursorViews.Quota;
import springboot.DTO.CursorViews.Repo;
import springboot.DTO.CursorViews.RepoList;
import springboot.DTO.CursorViews.TaskCard;
import springboot.DTO.CursorViews.TaskDetail;
import springboot.domain.SysUsers;
import springboot.service.RegCodeAccessService;
import springboot.service.cursor.CursorCloudClient.Created;
import springboot.service.cursor.CursorCloudClient.RemoteAgent;
import springboot.service.cursor.CursorCloudClient.RemoteRepo;
import springboot.service.cursor.CursorCloudClient.RemoteRun;
import springboot.service.cursor.CursorCloudClient.RemoteUsage;
import springboot.utils.BizException;
import springboot.utils.ForbiddenException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每个登录用户只用自己的 Cursor Key。列表来自该 Key 在 Cursor 上的云端任务，按用户隔开。
 */
@Service
public class CursorTaskService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int RESULT_LIMIT = 100_000;
    private static final long REPO_CACHE_MS = 10 * 60_000L;

    private final JdbcTemplate jdbc;
    private final CursorKeyCipher cipher;
    private final CursorCloudClient client;
    private final RegCodeAccessService access;
    private final ConcurrentHashMap<String, RepoCache> repoCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> repoLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, QuotaCache> quotaCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> quotaLocks = new ConcurrentHashMap<>();

    public CursorTaskService(JdbcTemplate jdbc, CursorKeyCipher cipher, CursorCloudClient client,
                             RegCodeAccessService access) {
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
            throw new ForbiddenException("无权使用 Cursor 任务");
        }
        if (access.isRootUser(user)) {
            return user;
        }
        SysUsers governing = access.appMenuGoverningUser(user);
        if (governing == null) {
            throw new ForbiddenException("无权使用 Cursor 任务");
        }
        if (access.isRootUser(governing)) {
            return user;
        }
        String menuId = cursorMenuId();
        if (menuId == null || !access.hasMenu(governing, menuId)) {
            throw new ForbiddenException("无权使用 Cursor 任务");
        }
        return user;
    }

    public Board board(String userId) {
        KeyRow key = findKey(userId);
        String warning = "";
        if (key != null) {
            try {
                syncRemote(userId, client.listAgents(openKey(key), 100));
            } catch (BizException e) {
                warning = e.getMessage();
            }
        }
        List<AgentRow> rows = listRows(userId);
        List<TaskCard> cards = new ArrayList<>();
        for (AgentRow row : rows) {
            cards.add(new TaskCard(row.id, row.name, row.agentStatus, row.runStatus, row.repoUrl, row.updatedAt));
        }
        return new Board(key != null, key == null ? "" : key.hint, warning, cards);
    }

    public void saveKey(String userId, String apiKey) {
        String trimmed = apiKey == null ? "" : apiKey.trim();
        if (trimmed.length() < 16 || trimmed.length() > 200 || hasWhitespace(trimmed)) {
            throw new BizException("Key 格式不对。到 Cursor Dashboard → Integrations 创建一个 User API Key");
        }
        if (!cipher.ready()) {
            throw new BizException("服务器还没配置 CURSOR_KEY_SECRET，暂时不能保存 Key");
        }
        client.verify(trimmed);
        String hint = trimmed.substring(trimmed.length() - 4);
        String packed;
        try {
            packed = cipher.seal(trimmed);
        } catch (IllegalStateException e) {
            throw new BizException("服务器还没配置 CURSOR_KEY_SECRET，暂时不能保存 Key");
        }
        jdbc.update(
                "INSERT INTO cursor_user_key (user_id, key_cipher, key_hint, update_time) VALUES (?, ?, ?, NOW()) "
                        + "ON DUPLICATE KEY UPDATE key_cipher = VALUES(key_cipher), key_hint = VALUES(key_hint), update_time = NOW()",
                userId, packed, hint);
        repoCache.remove(userId);
        quotaCache.remove(userId);
    }

    public void clearKey(String userId) {
        jdbc.update("DELETE FROM cursor_user_key WHERE user_id = ?", userId);
        repoCache.remove(userId);
        quotaCache.remove(userId);
    }

    /** Cursor Models 剩余额度。结果缓存 10 分钟，失败时不挡住任务列表。 */
    public Quota quota(String userId) {
        Object lock = quotaLocks.computeIfAbsent(userId, id -> new Object());
        synchronized (lock) {
            QuotaCache cached = quotaCache.get(userId);
            long now = System.currentTimeMillis();
            if (cached != null && now - cached.at < REPO_CACHE_MS) {
                return cached.quota;
            }
            try {
                RemoteUsage remote = client.fetchUsage(openKey(requireKey(userId)));
                Quota quota = new Quota(remote.available(), remote.unlimited(), remote.remainingPercent(),
                        remote.resetAt() == null ? "" : remote.resetAt(), remote.warning() == null ? "" : remote.warning());
                if (quota.available()) {
                    quotaCache.put(userId, new QuotaCache(quota, now));
                }
                return quota;
            } catch (BizException e) {
                if (cached != null) {
                    return new Quota(cached.quota.available(), cached.quota.unlimited(), cached.quota.remainingPercent(),
                            cached.quota.resetAt(), e.getMessage());
                }
                return new Quota(false, false, null, "", e.getMessage());
            }
        }
    }

    /** 新建任务时可选的仓库。Cursor 限制大约每分钟一次，结果缓存 10 分钟。 */
    public RepoList repositories(String userId) {
        Object lock = repoLocks.computeIfAbsent(userId, id -> new Object());
        synchronized (lock) {
            RepoCache cached = repoCache.get(userId);
            long now = System.currentTimeMillis();
            if (cached != null && now - cached.at < REPO_CACHE_MS) {
                return new RepoList(cached.repos, "");
            }
            try {
                List<Repo> repos = new ArrayList<>();
                for (RemoteRepo remote : client.listRepositories(openKey(requireKey(userId)))) {
                    repos.add(new Repo(remote.owner(), remote.name(), remote.url()));
                }
                repoCache.put(userId, new RepoCache(repos, now));
                return new RepoList(repos, "");
            } catch (BizException e) {
                if (cached != null) {
                    return new RepoList(cached.repos, e.getMessage());
                }
                throw e;
            }
        }
    }

    public TaskDetail create(String userId, String prompt, String repoUrl, String startingRef, Boolean autoCreatePr) {
        String text = requirePrompt(prompt);
        String repo = requireRepo(repoUrl);
        String ref = startingRef == null ? "" : startingRef.trim();
        if (ref.length() > 120) {
            throw new BizException("起始分支太长");
        }
        String name = deriveName(text);
        Created created = client.createAgent(openKey(requireKey(userId)), name, text, repo, ref, autoCreatePr == null || autoCreatePr);
        if (created.agent().id().isBlank()) {
            throw new BizException("Cursor 没有返回任务编号");
        }
        String id = newId();
        RemoteRun run = created.run();
        jdbc.update(
                "INSERT INTO cursor_agent (id, user_id, agent_id, name, prompt_preview, repo_url, starting_ref, "
                        + "agent_status, run_status, latest_run_id, result_text, agent_url, pr_url, branch_name, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                id, userId, created.agent().id(), first(created.agent().name(), name), preview(text), repo, ref,
                first(created.agent().status(), "ACTIVE"), run.status(), blankToNull(run.id()),
                clip(run.result()), first(created.agent().url(), ""), blankToNull(run.prUrl()), blankToNull(run.branch()));
        return detail(userId, id, false);
    }

    public TaskDetail detail(String userId, String id, boolean refresh) {
        AgentRow row = requireOwned(userId, id);
        if (refresh) {
            refreshOne(row);
        }
        return toDetail(row);
    }

    public TaskDetail follow(String userId, String id, String prompt) {
        AgentRow row = requireOwned(userId, id);
        String text = requirePrompt(prompt);
        if (busy(row)) {
            throw new BizException("上一条还在执行，等它结束或先停止");
        }
        RemoteRun run = client.createRun(openKey(requireKey(userId)), row.agentId, text);
        row.runStatus = first(run.status(), "CREATING");
        row.latestRunId = first(run.id(), row.latestRunId);
        row.agentStatus = "ACTIVE";
        jdbc.update(
                "UPDATE cursor_agent SET run_status = ?, latest_run_id = ?, agent_status = ?, update_time = NOW() "
                        + "WHERE id = ? AND user_id = ?",
                row.runStatus, blankToNull(row.latestRunId), row.agentStatus, row.id, userId);
        return toDetail(row);
    }

    public TaskDetail cancel(String userId, String id) {
        AgentRow row = requireOwned(userId, id);
        if (row.latestRunId == null || row.latestRunId.isBlank()) {
            throw new BizException("没有可停止的执行");
        }
        client.cancelRun(openKey(requireKey(userId)), row.agentId, row.latestRunId);
        row.runStatus = "CANCELLED";
        jdbc.update("UPDATE cursor_agent SET run_status = 'CANCELLED', update_time = NOW() WHERE id = ? AND user_id = ?",
                row.id, userId);
        return toDetail(row);
    }

    private void refreshOne(AgentRow row) {
        KeyRow key = findKey(row.userId);
        if (key == null) {
            return;
        }
        String apiKey = openKey(key);
        RemoteAgent agent = client.getAgent(apiKey, row.agentId);
        if (agent == null) {
            row.agentStatus = "ARCHIVED";
            jdbc.update("UPDATE cursor_agent SET agent_status = 'ARCHIVED', update_time = NOW() WHERE id = ? AND user_id = ?",
                    row.id, row.userId);
            return;
        }
        row.name = first(agent.name(), row.name);
        row.agentStatus = first(agent.status(), row.agentStatus);
        row.agentUrl = first(agent.url(), row.agentUrl);
        row.repoUrl = first(agent.repoUrl(), row.repoUrl);
        row.latestRunId = first(agent.latestRunId(), row.latestRunId);
        if (!row.latestRunId.isBlank()) {
            RemoteRun run = client.getRun(apiKey, row.agentId, row.latestRunId);
            row.runStatus = first(run.status(), row.runStatus);
            if (!run.result().isBlank()) {
                row.resultText = clip(run.result());
            }
            row.prUrl = first(run.prUrl(), row.prUrl);
            row.branchName = first(run.branch(), row.branchName);
        }
        jdbc.update(
                "UPDATE cursor_agent SET name = ?, agent_status = ?, agent_url = ?, repo_url = ?, latest_run_id = ?, run_status = ?, "
                        + "result_text = ?, pr_url = ?, branch_name = ?, update_time = NOW() WHERE id = ? AND user_id = ?",
                row.name, row.agentStatus, row.agentUrl, blankToNull(row.repoUrl), blankToNull(row.latestRunId), row.runStatus,
                row.resultText, blankToNull(row.prUrl), blankToNull(row.branchName), row.id, row.userId);
    }

    /** 把这把 Key 在 Cursor 上已有的云端任务写入本地。已有行只刷新名称和状态，不覆盖结果正文。 */
    private void syncRemote(String userId, List<RemoteAgent> remote) {
        for (RemoteAgent agent : remote) {
            if (agent.id() == null || agent.id().isBlank()) {
                continue;
            }
            String name = clipName(first(agent.name(), "未命名任务"));
            Timestamp when = parseTime(agent.updatedAt());
            jdbc.update(
                    "INSERT INTO cursor_agent (id, user_id, agent_id, name, prompt_preview, repo_url, starting_ref, "
                            + "agent_status, run_status, latest_run_id, result_text, agent_url, create_time, update_time) "
                            + "VALUES (?, ?, ?, ?, ?, ?, '', ?, '', ?, '', ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE name = VALUES(name), agent_status = VALUES(agent_status), "
                            + "agent_url = VALUES(agent_url), latest_run_id = IFNULL(VALUES(latest_run_id), latest_run_id), "
                            + "repo_url = IF(VALUES(repo_url) IS NULL OR VALUES(repo_url) = '', repo_url, VALUES(repo_url)), "
                            + "update_time = VALUES(update_time)",
                    newId(), userId, agent.id(), name, name, blankToNull(agent.repoUrl()),
                    first(agent.status(), "IDLE"), blankToNull(agent.latestRunId()), blankToNull(agent.url()), when, when);
        }
    }

    private KeyRow requireKey(String userId) {
        KeyRow key = findKey(userId);
        if (key == null) {
            throw new BizException("先保存你自己的 Cursor API Key");
        }
        return key;
    }

    private String openKey(KeyRow key) {
        try {
            return cipher.open(key.cipher);
        } catch (IllegalStateException e) {
            if (!cipher.ready()) {
                throw new BizException("服务器还没配置 CURSOR_KEY_SECRET，暂时不能使用已保存的 Key");
            }
            throw new BizException("保存的 Key 无法读取，请重新填写");
        }
    }

    private KeyRow findKey(String userId) {
        List<KeyRow> rows = jdbc.query(
                "SELECT user_id, key_cipher, key_hint FROM cursor_user_key WHERE user_id = ?",
                (rs, i) -> new KeyRow(rs.getString("user_id"), rs.getString("key_cipher"), rs.getString("key_hint")),
                userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<AgentRow> listRows(String userId) {
        return jdbc.query(
                "SELECT id, user_id, agent_id, name, prompt_preview, repo_url, starting_ref, agent_status, run_status, "
                        + "latest_run_id, result_text, agent_url, pr_url, branch_name, update_time "
                        + "FROM cursor_agent WHERE user_id = ? ORDER BY update_time DESC, create_time DESC LIMIT 100",
                (rs, i) -> mapRow(rs), userId);
    }

    private AgentRow requireOwned(String userId, String id) {
        if (id == null || id.isBlank() || id.length() > 64) {
            throw new BizException("任务不存在");
        }
        List<AgentRow> rows = jdbc.query(
                "SELECT id, user_id, agent_id, name, prompt_preview, repo_url, starting_ref, agent_status, run_status, "
                        + "latest_run_id, result_text, agent_url, pr_url, branch_name, update_time "
                        + "FROM cursor_agent WHERE id = ? AND user_id = ?",
                (rs, i) -> mapRow(rs), id, userId);
        if (rows.isEmpty()) {
            throw new BizException("任务不存在");
        }
        return rows.get(0);
    }

    private String cursorMenuId() {
        List<String> ids = jdbc.queryForList(
                "SELECT id FROM sys_menus WHERE client = 'app' AND disabled = 0 AND LOWER(route_key) = '/cursor' LIMIT 1",
                String.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private static AgentRow mapRow(ResultSet rs) throws SQLException {
        AgentRow row = new AgentRow();
        row.id = rs.getString("id");
        row.userId = rs.getString("user_id");
        row.agentId = rs.getString("agent_id");
        row.name = nz(rs.getString("name"));
        row.promptPreview = nz(rs.getString("prompt_preview"));
        row.repoUrl = nz(rs.getString("repo_url"));
        row.startingRef = nz(rs.getString("starting_ref"));
        row.agentStatus = nz(rs.getString("agent_status"));
        row.runStatus = nz(rs.getString("run_status"));
        row.latestRunId = nz(rs.getString("latest_run_id"));
        row.resultText = nz(rs.getString("result_text"));
        row.agentUrl = nz(rs.getString("agent_url"));
        row.prUrl = nz(rs.getString("pr_url"));
        row.branchName = nz(rs.getString("branch_name"));
        row.updatedAt = timeText(rs.getObject("update_time"));
        return row;
    }

    private static TaskDetail toDetail(AgentRow row) {
        return new TaskDetail(row.id, row.name, row.agentStatus, row.runStatus, row.promptPreview, row.repoUrl,
                row.startingRef, row.resultText, row.agentUrl, row.prUrl, row.branchName, row.updatedAt);
    }

    private static boolean busy(AgentRow row) {
        return "ACTIVE".equals(row.agentStatus) || "CREATING".equals(row.runStatus) || "RUNNING".equals(row.runStatus);
    }

    private static String requirePrompt(String prompt) {
        String text = prompt == null ? "" : prompt.trim();
        if (text.isEmpty()) {
            throw new BizException("先写要做什么");
        }
        if (text.length() > 8000) {
            throw new BizException("指令太长，请控制在 8000 字以内");
        }
        return text;
    }

    private static String requireRepo(String repoUrl) {
        String repo = repoUrl == null ? "" : repoUrl.trim();
        if (!(repo.startsWith("https://") || repo.startsWith("http://")) || repo.length() > 300) {
            throw new BizException("仓库地址需要是 http(s) 链接");
        }
        return repo;
    }

    static String deriveName(String prompt) {
        String line = prompt.trim();
        int nl = line.indexOf('\n');
        if (nl >= 0) {
            line = line.substring(0, nl).trim();
        }
        if (line.length() > 40) {
            line = line.substring(0, 40);
        }
        return line.isEmpty() ? "未命名任务" : line;
    }

    private static Timestamp parseTime(String iso) {
        if (iso == null || iso.isBlank()) {
            return Timestamp.from(Instant.now());
        }
        try {
            return Timestamp.from(Instant.parse(iso));
        } catch (Exception e) {
            return Timestamp.from(Instant.now());
        }
    }

    private static String clipName(String name) {
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    private static String preview(String prompt) {
        String flat = prompt.replace('\n', ' ').trim();
        return flat.length() > 180 ? flat.substring(0, 180) : flat;
    }

    private static String clip(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > RESULT_LIMIT ? text.substring(0, RESULT_LIMIT) : text;
    }

    private static String timeText(Object value) {
        if (value instanceof LocalDateTime time) {
            return time.format(TIME);
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(TIME);
        }
        return value == null ? "" : String.valueOf(value);
    }

    private static String first(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? nz(fallback) : preferred;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean hasWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record RepoCache(List<Repo> repos, long at) {
    }

    private record QuotaCache(Quota quota, long at) {
    }

    private static final class KeyRow {
        final String userId;
        final String cipher;
        final String hint;

        KeyRow(String userId, String cipher, String hint) {
            this.userId = userId;
            this.cipher = cipher;
            this.hint = hint == null ? "" : hint;
        }
    }

    private static final class AgentRow {
        String id;
        String userId;
        String agentId;
        String name;
        String promptPreview;
        String repoUrl;
        String startingRef;
        String agentStatus;
        String runStatus;
        String latestRunId;
        String resultText;
        String agentUrl;
        String prUrl;
        String branchName;
        String updatedAt;
    }
}
