package springboot.service.cursor;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import springboot.utils.BizException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cloud Agents API v1。认证是 Basic，用户名是用户自己的 API Key，密码为空。
 */
public class CursorCloudClient {

    private static final Pattern USED_PERCENT = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*%");
    private static final String USAGE_SUMMARY = "https://cursor.com/api/usage-summary";
    private static final String PERIOD_USAGE = "https://api2.cursor.sh/aiserver.v1.DashboardService/GetCurrentPeriodUsage";

    private final RestTemplate http;
    private final String apiBase;
    private final JsonMapper json;

    public CursorCloudClient(RestTemplate http, String apiBase, JsonMapper json) {
        this.http = http;
        this.apiBase = trimBase(apiBase);
        this.json = json;
    }

    public static RestTemplate defaultHttp() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(8));
        factory.setReadTimeout(Duration.ofSeconds(90));
        return new RestTemplate(factory);
    }

    public void verify(String apiKey) {
        exchange(apiKey, HttpMethod.GET, "/v1/agents?limit=1", null);
    }

    /**
     * Cursor Models 池的剩余额度。先读 dashboard 的 usage-summary，失败再读周期用量。
     * 两个接口都只认登录会话时，会抛出读不到额度，不影响任务列表。
     */
    public RemoteUsage fetchUsage(String apiKey) {
        BizException last = null;
        RemoteUsage summary = tryUsage(apiKey, HttpMethod.GET, USAGE_SUMMARY, null);
        if (summary != null && summary.available()) {
            return summary;
        }
        RemoteUsage period = tryUsage(apiKey, HttpMethod.POST, PERIOD_USAGE, Map.of());
        if (period != null && period.available()) {
            return period;
        }
        if (summary != null && summary.warning() != null && !summary.warning().isBlank()) {
            last = new BizException(summary.warning());
        }
        if (period != null && period.warning() != null && !period.warning().isBlank()) {
            last = new BizException(period.warning());
        }
        throw last == null ? new BizException("这个 Key 读不到 Cursor Models 额度") : last;
    }

    private RemoteUsage tryUsage(String apiKey, HttpMethod method, String url, Object body) {
        try {
            return parseUsage(exchangeUrl(apiKey, method, url, body));
        } catch (BizException e) {
            if ("Cursor Key 无效或没有权限，请重新创建".equals(e.getMessage()) || "Cursor 上已经没有这个任务".equals(e.getMessage())) {
                return new RemoteUsage(false, false, null, "", "这个 Key 读不到 Cursor Models 额度");
            }
            return new RemoteUsage(false, false, null, "", e.getMessage());
        }
    }

    static RemoteUsage parseUsage(JsonNode root) {
        if (root == null || root.isNull()) {
            return new RemoteUsage(false, false, null, "", "这个 Key 读不到 Cursor Models 额度");
        }
        if (bool(root, "isUnlimited")) {
            return new RemoteUsage(true, true, null, text(root, "billingCycleEnd"), "");
        }
        JsonNode plan = root.path("individualUsage").path("plan");
        if (plan.isMissingNode() || plan.isNull()) {
            plan = root.path("planUsage");
        }
        if (plan.isMissingNode() || !plan.isObject()) {
            plan = root;
        }
        Double used = num(plan, "autoPercentUsed");
        if (used == null) {
            used = percentIn(text(root, "autoModelSelectedDisplayMessage"));
        }
        if (used == null) {
            used = percentIn(text(root, "displayMessage"));
        }
        if (used == null) {
            Double remaining = num(plan, "remaining");
            Double limit = num(plan, "limit");
            if (remaining != null && limit != null && limit > 0) {
                used = 100.0 - (remaining * 100.0 / limit);
            }
        }
        if (used == null) {
            return new RemoteUsage(false, false, null, "", "这个 Key 读不到 Cursor Models 额度");
        }
        int left = (int) Math.round(100.0 - used);
        if (left < 0) {
            left = 0;
        }
        if (left > 100) {
            left = 100;
        }
        return new RemoteUsage(true, false, left, text(root, "billingCycleEnd"), "");
    }

    private static Double percentIn(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher matcher = USED_PERCENT.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        return Double.parseDouble(matcher.group(1));
    }

    private static boolean bool(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && !value.isNull() && value.asBoolean(false);
    }

    private static Double num(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.numberValue().doubleValue();
    }

    /**
     * 这个 Key 的 Cursor 账号能访问的仓库。官方限制大约 1 次/分钟，响应可能要几十秒。
     */
    public List<RemoteRepo> listRepositories(String apiKey) {
        JsonNode root;
        try {
            root = exchange(apiKey, HttpMethod.GET, "/v0/repositories", null);
        } catch (BizException e) {
            if ("Cursor Key 无效或没有权限，请重新创建".equals(e.getMessage())) {
                throw new BizException("读不到仓库。确认这个 Key 的 Cursor 账号已经连上 GitHub");
            }
            throw e;
        }
        List<RemoteRepo> repos = new ArrayList<>();
        JsonNode arr = root == null ? null : root.get("repositories");
        if (arr != null && arr.isArray()) {
            for (JsonNode item : arr) {
                String url = text(item, "repository");
                if (url.isBlank()) {
                    url = text(item, "url");
                }
                url = url.trim();
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    continue;
                }
                String owner = text(item, "owner").trim();
                String name = text(item, "name").trim();
                if (name.isBlank()) {
                    name = url;
                }
                repos.add(new RemoteRepo(owner, name, url));
            }
        }
        repos.sort(Comparator.comparing((RemoteRepo r) -> r.owner().toLowerCase()).thenComparing(r -> r.name().toLowerCase()));
        return repos;
    }

    /** 该 Key 所属 Cursor 账号的云端任务，新的在前。单页最多 100，最多翻 2 页。 */
    public List<RemoteAgent> listAgents(String apiKey, int limit) {
        int cap = Math.min(Math.max(limit, 1), 200);
        List<RemoteAgent> all = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 2 && all.size() < cap; page++) {
            int n = Math.min(100, cap - all.size());
            String path = "/v1/agents?limit=" + n;
            if (cursor != null && !cursor.isBlank()) {
                path += "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
            }
            JsonNode root = exchange(apiKey, HttpMethod.GET, path, null);
            int before = all.size();
            JsonNode arr = root == null ? null : root.get("items");
            if (arr != null && arr.isArray()) {
                for (JsonNode item : arr) {
                    all.add(agentOf(item));
                }
            }
            String next = root == null ? "" : text(root, "nextCursor");
            if (next.isBlank() || all.size() == before) {
                break;
            }
            cursor = next;
        }
        return all;
    }

    /** @return null 表示 Cursor 上已经没有这个 agent */
    public RemoteAgent getAgent(String apiKey, String agentId) {
        try {
            return agentOf(exchange(apiKey, HttpMethod.GET, "/v1/agents/" + agentId, null));
        } catch (BizException e) {
            if ("Cursor 上已经没有这个任务".equals(e.getMessage())) {
                return null;
            }
            throw e;
        }
    }

    public RemoteRun getRun(String apiKey, String agentId, String runId) {
        JsonNode root = exchange(apiKey, HttpMethod.GET, "/v1/agents/" + agentId + "/runs/" + runId, null);
        return runOf(root);
    }

    public Created createAgent(String apiKey, String name, String prompt, String repoUrl, String startingRef, boolean autoCreatePr) {
        Map<String, Object> repo = new LinkedHashMap<>();
        repo.put("url", repoUrl);
        if (startingRef != null && !startingRef.isBlank()) {
            repo.put("startingRef", startingRef.trim());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", Map.of("text", prompt));
        body.put("name", name);
        body.put("repos", List.of(repo));
        body.put("autoCreatePR", autoCreatePr);
        JsonNode root = exchange(apiKey, HttpMethod.POST, "/v1/agents", body);
        return new Created(agentOf(root == null ? null : root.get("agent")), runOf(root == null ? null : root.get("run")));
    }

    public RemoteRun createRun(String apiKey, String agentId, String prompt) {
        JsonNode root = exchange(apiKey, HttpMethod.POST, "/v1/agents/" + agentId + "/runs",
                Map.of("prompt", Map.of("text", prompt)));
        return runOf(root == null ? null : root.get("run"));
    }

    public void cancelRun(String apiKey, String agentId, String runId) {
        exchange(apiKey, HttpMethod.POST, "/v1/agents/" + agentId + "/runs/" + runId + "/cancel", Map.of());
    }

    private JsonNode exchangeUrl(String apiKey, HttpMethod method, String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("Origin", "https://cursor.com");
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (url.contains("aiserver.v1")) {
                headers.set("Connect-Protocol-Version", "1");
            }
        }
        String payload = body == null ? null : json.writeValueAsString(body);
        try {
            String raw = http.exchange(URI.create(url), method, new HttpEntity<>(payload, headers), String.class).getBody();
            if (raw == null || raw.isBlank() || raw.charAt(0) == '<') {
                return null;
            }
            return json.readTree(raw);
        } catch (HttpStatusCodeException e) {
            throw fail(e, apiKey);
        } catch (ResourceAccessException e) {
            throw new BizException("连接 Cursor 失败，请稍后再试");
        }
    }

    private JsonNode exchange(String apiKey, HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(apiKey, "");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        String payload = body == null ? null : json.writeValueAsString(body);
        try {
            String raw = http.exchange(URI.create(apiBase + path), method, new HttpEntity<>(payload, headers), String.class).getBody();
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return json.readTree(raw);
        } catch (HttpStatusCodeException e) {
            throw fail(e, apiKey);
        } catch (ResourceAccessException e) {
            throw new BizException("连接 Cursor 失败，请稍后再试");
        }
    }

    private BizException fail(HttpStatusCodeException e, String apiKey) {
        int status = e.getStatusCode().value();
        if (status == 429) {
            return new BizException("Cursor 请求太频繁，请稍后再试");
        }
        if (status == 401 || status == 403) {
            return new BizException("Cursor Key 无效或没有权限，请重新创建");
        }
        if (status == 409) {
            return new BizException("上一条还在执行，等它结束或先停止");
        }
        if (status == 404) {
            return new BizException("Cursor 上已经没有这个任务");
        }
        String remote = extractMessage(e.getResponseBodyAsString());
        if (remote != null && apiKey != null && !apiKey.isBlank()) {
            remote = remote.replace(apiKey, "***");
        }
        if (remote != null && !remote.isBlank()) {
            return new BizException(remote.length() > 200 ? remote.substring(0, 200) : remote);
        }
        return new BizException("Cursor 接口失败（" + status + "）");
    }

    private String extractMessage(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            JsonNode node = json.readTree(raw);
            String direct = text(node, "message");
            if (!direct.isBlank()) {
                return direct;
            }
            JsonNode err = node.get("error");
            return err == null ? "" : text(err, "message");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static RemoteAgent agentOf(JsonNode node) {
        if (node == null || node.isNull()) {
            return new RemoteAgent("", "", "", "", "", "", "");
        }
        String repo = "";
        JsonNode repos = node.get("repos");
        if (repos != null && repos.isArray() && repos.size() > 0) {
            repo = text(repos.get(0), "url");
        }
        return new RemoteAgent(text(node, "id"), text(node, "name"), text(node, "status"), text(node, "url"),
                text(node, "latestRunId"), text(node, "updatedAt"), repo);
    }

    private static RemoteRun runOf(JsonNode node) {
        if (node == null || node.isNull()) {
            return new RemoteRun("", "", "", "", "");
        }
        String branch = "";
        String pr = "";
        JsonNode git = node.get("git");
        JsonNode branches = git == null ? null : git.get("branches");
        if (branches != null && branches.isArray() && branches.size() > 0) {
            JsonNode first = branches.get(0);
            branch = text(first, "branch");
            pr = text(first, "prUrl");
        }
        return new RemoteRun(text(node, "id"), text(node, "status"), text(node, "result"), branch, pr);
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        String s = value.asString();
        return s == null ? "" : s;
    }

    private static String trimBase(String apiBase) {
        String base = apiBase == null || apiBase.isBlank() ? "https://api.cursor.com" : apiBase.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    public record RemoteAgent(String id, String name, String status, String url, String latestRunId, String updatedAt, String repoUrl) {
    }

    public record RemoteRun(String id, String status, String result, String branch, String prUrl) {
    }

    public record Created(RemoteAgent agent, RemoteRun run) {
    }

    public record RemoteRepo(String owner, String name, String url) {
    }

    public record RemoteUsage(boolean available, boolean unlimited, Integer remainingPercent, String resetAt, String warning) {
    }
}
