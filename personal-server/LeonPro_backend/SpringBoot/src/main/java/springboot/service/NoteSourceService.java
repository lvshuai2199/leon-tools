package springboot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import springboot.notes.NoteDraftNames;
import springboot.notes.NoteGitClient;
import springboot.notes.NoteIndexer;
import springboot.notes.NotePaths;
import springboot.notes.NoteRepoUrls;
import springboot.notes.NoteSyncException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 一个笔记仓库：保存地址和分支，浅克隆后索引 Markdown，远程有新提交就更新。
 * 随手记先写在 note_draft，点上传才提交并推到这个分支。
 */
@Slf4j
@Service
public class NoteSourceService {

    static final String ID = "default";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("HHmmss");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final NoteGitClient git;
    private final ReentrantLock lock = new ReentrantLock();
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "note-sync");
        t.setDaemon(true);
        return t;
    });

    @Value("${app.notes.storage-dir:./data/notes-repo}")
    private String storageDir;
    @Value("${app.notes.max-files:8000}")
    private int maxFiles;
    @Value("${app.notes.max-file-bytes:1048576}")
    private int maxFileBytes;
    @Value("${app.notes.max-asset-bytes:20971520}")
    private int maxAssetBytes;
    @Value("${app.notes.draft-dir:随手记}")
    private String draftDir;
    @Value("${app.notes.commit-name:LeonPro}")
    private String commitName;
    @Value("${app.notes.commit-email:notes@localhost}")
    private String commitEmail;

    public NoteSourceService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, NoteGitClient git) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.git = git;
    }

    public Map<String, Object> sourceView() {
        Source s = load();
        Map<String, Object> view = new LinkedHashMap<>();
        if (s == null) {
            view.put("configured", false);
            view.put("repoUrl", "");
            view.put("branch", "");
            view.put("tokenSet", false);
            view.put("status", "idle");
            view.put("lastCommit", null);
            view.put("lastSyncTime", null);
            view.put("lastCheckTime", null);
            view.put("lastError", null);
            view.put("fileCount", 0);
            return view;
        }
        view.put("configured", s.repoUrl() != null && !s.repoUrl().isBlank());
        view.put("repoUrl", s.repoUrl());
        view.put("branch", s.branch());
        view.put("tokenSet", s.token() != null && !s.token().isBlank());
        view.put("status", s.status() == null ? "idle" : s.status());
        view.put("lastCommit", s.lastCommit());
        view.put("lastSyncTime", fmt(s.lastSyncTime()));
        view.put("lastCheckTime", fmt(s.lastCheckTime()));
        view.put("lastError", s.lastError());
        view.put("fileCount", s.fileCount());
        return view;
    }

    public void save(String repoUrl, String branch, String accessToken, boolean clearToken) {
        String url = NoteRepoUrls.normalizeRepoUrl(repoUrl);
        String br = NoteRepoUrls.normalizeBranch(branch);
        String token = NoteRepoUrls.normalizeToken(accessToken);
        lock.lock();
        try {
            Source cur = load();
            boolean changed = cur == null || !url.equals(cur.repoUrl()) || !br.equals(cur.branch());
            String stored;
            if (clearToken || (changed && token.isEmpty())) {
                stored = null;
            } else if (!token.isEmpty()) {
                stored = token;
            } else {
                stored = cur == null ? null : cur.token();
            }
            if (cur == null) {
                jdbc.update("INSERT INTO note_source (id, repo_url, branch, access_token, sync_status, file_count, enabled) "
                        + "VALUES (?, ?, ?, ?, 'idle', 0, 1)", ID, url, br, stored);
            } else if (changed) {
                jdbc.update("UPDATE note_source SET repo_url = ?, branch = ?, access_token = ?, sync_status = 'idle', "
                                + "last_commit = NULL, last_error = NULL, file_count = 0, enabled = 1 WHERE id = ?",
                        url, br, stored, ID);
                jdbc.update("DELETE FROM note_doc");
            } else {
                jdbc.update("UPDATE note_source SET access_token = ?, enabled = 1 WHERE id = ?", stored, ID);
            }
        } finally {
            lock.unlock();
        }
        syncAsync();
    }

    public List<String> listBranches(String repoUrl, String accessToken) {
        String url = NoteRepoUrls.normalizeRepoUrl(repoUrl);
        String token = NoteRepoUrls.normalizeToken(accessToken);
        if (token.isEmpty()) {
            Source cur = load();
            if (cur != null && url.equals(cur.repoUrl()) && cur.token() != null) {
                token = cur.token();
            }
        }
        List<String> branches = git.listBranches(url, token);
        if (branches.isEmpty()) {
            throw new NoteSyncException("这个仓库没有分支");
        }
        return branches;
    }

    public void syncAsync() {
        syncExecutor.execute(this::syncBlocking);
    }

    public void syncBlocking() {
        lock.lock();
        try {
            Source s = load();
            if (s == null || blank(s.repoUrl()) || blank(s.branch())) {
                return;
            }
            doSync(s);
        } finally {
            lock.unlock();
        }
    }

    public void poll() {
        if (!lock.tryLock()) {
            return;
        }
        try {
            Source s = load();
            if (s == null || s.enabled() != 1 || blank(s.repoUrl()) || blank(s.branch())) {
                return;
            }
            String remote;
            try {
                remote = git.remoteHead(s.repoUrl(), s.token(), s.branch());
            } catch (Exception e) {
                markError(e, s.token());
                return;
            }
            jdbc.update("UPDATE note_source SET last_check_time = NOW() WHERE id = ?", ID);
            if (remote == null) {
                jdbc.update("UPDATE note_source SET sync_status = 'error', last_error = ? WHERE id = ?",
                        "远程没有这个分支", ID);
                return;
            }
            if (remote.equals(s.lastCommit()) && "ok".equals(s.status())) {
                if (s.lastError() != null && !s.lastError().startsWith("有 ")) {
                    jdbc.update("UPDATE note_source SET last_error = NULL WHERE id = ?", ID);
                }
                return;
            }
            doSync(s);
        } finally {
            lock.unlock();
        }
    }

    public List<Map<String, Object>> listDocs() {
        return jdbc.query("SELECT path, title, size_bytes FROM note_doc ORDER BY path", (rs, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", rs.getString("path"));
            row.put("title", rs.getString("title"));
            row.put("size", rs.getInt("size_bytes"));
            return row;
        });
    }

    public Map<String, Object> readDoc(String path) {
        String rel = NotePaths.normalizeRel(path);
        if (!isMarkdown(rel)) {
            throw new IllegalArgumentException("只能阅读 Markdown 文件");
        }
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM note_doc WHERE path = ?", Integer.class, rel);
        if (n == null || n == 0) {
            throw new IllegalArgumentException("文档不存在");
        }
        try {
            Path file = NotePaths.resolve(workDir(), rel);
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("文档不存在");
            }
            long size = Files.size(file);
            if (size > maxFileBytes) {
                throw new IllegalArgumentException("文档超过大小限制");
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String title = jdbc.query("SELECT title FROM note_doc WHERE path = ?",
                    rs -> rs.next() ? rs.getString(1) : rel, rel);
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("path", rel);
            view.put("title", title);
            view.put("content", content);
            return view;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("文档读取失败");
        }
    }

    public NoteAsset readAsset(String path) {
        String rel = NotePaths.normalizeRel(path);
        String ext = extension(rel);
        if (!ASSET_TYPES.containsKey(ext)) {
            throw new IllegalArgumentException("不支持预览这个文件");
        }
        try {
            Path file = NotePaths.resolve(workDir(), rel);
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("文件不存在");
            }
            long size = Files.size(file);
            if (size > maxAssetBytes) {
                throw new IllegalArgumentException("文件超过大小限制");
            }
            byte[] bytes = Files.readAllBytes(file);
            String name = file.getFileName() == null ? "file" : file.getFileName().toString();
            boolean inline = ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("gif")
                    || ext.equals("webp") || ext.equals("bmp") || ext.equals("avif") || ext.equals("ico");
            return new NoteAsset(bytes, ASSET_TYPES.get(ext), name, inline);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("文件读取失败");
        }
    }

    public List<Map<String, Object>> listDrafts() {
        return jdbc.query("SELECT id, title, update_time, SUBSTRING(content, 1, 120) AS excerpt FROM note_draft "
                + "ORDER BY update_time DESC", (rs, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("title", rs.getString("title"));
            row.put("excerpt", oneLine(rs.getString("excerpt")));
            row.put("updateTime", fmt(rs.getTimestamp("update_time")));
            return row;
        });
    }

    public Map<String, Object> readDraft(String id) {
        Draft d = requireDraft(id);
        return draftView(d);
    }

    public Map<String, Object> saveDraft(String id, String title, String content) {
        String safeTitle = title == null ? "" : title.trim();
        if (safeTitle.length() > 200) {
            safeTitle = safeTitle.substring(0, 200);
        }
        String body = content == null ? "" : content;
        if (body.length() > maxFileBytes) {
            throw new IllegalArgumentException("内容超过 1MB，先拆短再缓存");
        }
        String draftId = id == null || id.isBlank() ? newId() : normalizeId(id);
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM note_draft WHERE id = ?", Integer.class, draftId);
        if (n != null && n > 0) {
            jdbc.update("UPDATE note_draft SET title = ?, content = ? WHERE id = ?", safeTitle, body, draftId);
        } else if (id == null || id.isBlank()) {
            jdbc.update("INSERT INTO note_draft (id, title, content) VALUES (?, ?, ?)", draftId, safeTitle, body);
        } else {
            throw new IllegalArgumentException("缓存不存在");
        }
        return draftView(requireDraft(draftId));
    }

    public void deleteDraft(String id) {
        String draftId = normalizeId(id);
        int n = jdbc.update("DELETE FROM note_draft WHERE id = ?", draftId);
        if (n == 0) {
            throw new IllegalArgumentException("缓存不存在");
        }
    }

    public Map<String, Object> uploadDraft(String id) {
        String draftId = normalizeId(id);
        lock.lock();
        try {
            Draft d = requireDraft(draftId);
            String content = d.content() == null ? "" : d.content().replace("\r\n", "\n");
            if (content.isBlank()) {
                throw new IllegalArgumentException("内容是空的，还不能上传");
            }
            if (!content.endsWith("\n")) {
                content = content + "\n";
            }
            Source s = load();
            if (s == null || blank(s.repoUrl()) || blank(s.branch())) {
                throw new IllegalArgumentException("请先设置仓库地址和分支");
            }
            jdbc.update("UPDATE note_source SET sync_status = 'syncing', last_error = NULL WHERE id = ?", ID);
            try {
                Path dir = workDir();
                git.sync(dir, s.repoUrl(), s.token(), s.branch(), !sameCheckout(s));
                writeMeta(s);
                String rel = uniqueRel(dir, d.title());
                String title = NoteDraftNames.sanitizeTitle(d.title());
                String sha = git.publish(dir, s.token(), s.branch(), rel, content, "随手记: " + title,
                        commitName, commitEmail);
                finishIndex(dir, sha);
                jdbc.update("DELETE FROM note_draft WHERE id = ?", draftId);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("path", rel);
                out.put("commit", sha);
                out.put("title", title);
                return out;
            } catch (IllegalArgumentException | NoteSyncException e) {
                markError(e, s.token());
                throw e;
            } catch (Exception e) {
                markError(e, s.token());
                throw new NoteSyncException(NoteGitClient.publicMessage(e, s.token()));
            }
        } finally {
            lock.unlock();
        }
    }

    public record NoteAsset(byte[] bytes, String contentType, String filename, boolean inline) {
    }

    private void doSync(Source s) {
        jdbc.update("UPDATE note_source SET sync_status = 'syncing', last_error = NULL WHERE id = ?", ID);
        try {
            Path dir = workDir();
            String sha = git.sync(dir, s.repoUrl(), s.token(), s.branch(), !sameCheckout(s));
            writeMeta(s);
            finishIndex(dir, sha);
        } catch (Exception e) {
            markError(e, s.token());
            log.warn("笔记同步失败: {}", e.getMessage());
        }
    }

    private void finishIndex(Path dir, String sha) throws IOException {
        NoteIndexer.Result indexed = NoteIndexer.index(dir, maxFiles, maxFileBytes);
        replaceDocs(indexed.docs());
        String warn = indexed.skippedLarge() > 0
                ? "有 " + indexed.skippedLarge() + " 个 Markdown 超过大小限制或数量上限，未收录" : null;
        jdbc.update("UPDATE note_source SET sync_status = 'ok', last_commit = ?, last_sync_time = NOW(), "
                        + "last_check_time = NOW(), last_error = ?, file_count = ? WHERE id = ?",
                sha, warn, indexed.docs().size(), ID);
    }

    private void replaceDocs(List<NoteIndexer.Doc> docs) {
        tx.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM note_doc");
            jdbc.batchUpdate("INSERT INTO note_doc (id, path, title, size_bytes) VALUES (?, ?, ?, ?)",
                    docs, docs.size(), (ps, doc) -> {
                        ps.setString(1, newId());
                        ps.setString(2, doc.path());
                        ps.setString(3, doc.title());
                        ps.setInt(4, doc.sizeBytes());
                    });
        });
    }

    private String uniqueRel(Path dir, String title) throws IOException {
        String folder = draftFolder();
        String rel = NoteDraftNames.relativePath(folder, title, LocalDate.now(), null);
        Path file = NotePaths.resolve(dir, rel);
        if (!Files.exists(file)) {
            return rel;
        }
        return NoteDraftNames.relativePath(folder, title, LocalDate.now(), LocalTime.now().format(SUFFIX));
    }

    private String draftFolder() {
        String folder = draftDir == null || draftDir.isBlank() ? "随手记" : draftDir.trim();
        return NotePaths.normalizeRel(folder);
    }

    private void markError(Exception e, String token) {
        String msg = e instanceof NoteSyncException ? e.getMessage() : NoteGitClient.publicMessage(e, token);
        jdbc.update("UPDATE note_source SET sync_status = 'error', last_error = ?, last_check_time = NOW() WHERE id = ?",
                cut(msg, 1000), ID);
    }

    private boolean sameCheckout(Source s) {
        try {
            Path meta = metaFile();
            if (!Files.isDirectory(workDir().resolve(".git")) || !Files.isRegularFile(meta)) {
                return false;
            }
            String text = Files.readString(meta, StandardCharsets.UTF_8);
            String[] lines = text.split("\n", -1);
            return lines.length >= 2 && s.repoUrl().equals(lines[0]) && s.branch().equals(lines[1]);
        } catch (IOException e) {
            return false;
        }
    }

    private void writeMeta(Source s) throws IOException {
        Files.createDirectories(workDir().getParent() == null ? workDir() : workDir().getParent());
        Files.writeString(metaFile(), s.repoUrl() + "\n" + s.branch() + "\n", StandardCharsets.UTF_8);
    }

    private Path workDir() {
        return Paths.get(storageDir).toAbsolutePath().normalize();
    }

    private Path metaFile() {
        Path dir = workDir();
        Path parent = dir.getParent();
        String name = dir.getFileName().toString() + ".source";
        return parent == null ? Paths.get(name) : parent.resolve(name);
    }

    private Source load() {
        List<Source> rows = jdbc.query("SELECT repo_url, branch, access_token, last_commit, last_sync_time, "
                        + "last_check_time, last_error, sync_status, file_count, enabled FROM note_source WHERE id = ?",
                (rs, i) -> new Source(rs.getString("repo_url"), rs.getString("branch"), rs.getString("access_token"),
                        rs.getString("last_commit"), rs.getTimestamp("last_sync_time"), rs.getTimestamp("last_check_time"),
                        rs.getString("last_error"), rs.getString("sync_status"), rs.getInt("file_count"), rs.getInt("enabled")),
                ID);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Draft requireDraft(String id) {
        String draftId = normalizeId(id);
        List<Draft> rows = jdbc.query("SELECT id, title, content, update_time FROM note_draft WHERE id = ?",
                (rs, i) -> new Draft(rs.getString("id"), rs.getString("title"), rs.getString("content"),
                        rs.getTimestamp("update_time")),
                draftId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("缓存不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> draftView(Draft d) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", d.id());
        view.put("title", d.title() == null ? "" : d.title());
        view.put("content", d.content() == null ? "" : d.content());
        view.put("updateTime", fmt(d.updateTime()));
        return view;
    }

    private static String normalizeId(String id) {
        if (id == null || !id.matches("[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("缓存不存在");
        }
        return id.toLowerCase(Locale.ROOT);
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static boolean isMarkdown(String rel) {
        String lower = rel.toLowerCase(Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    private static String extension(String rel) {
        int dot = rel.lastIndexOf('.');
        if (dot < 0 || dot == rel.length() - 1) {
            return "";
        }
        return rel.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String oneLine(String excerpt) {
        if (excerpt == null) {
            return "";
        }
        return excerpt.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String fmt(Timestamp ts) {
        if (ts == null) {
            return null;
        }
        return ts.toLocalDateTime().format(TIME);
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static final Map<String, String> ASSET_TYPES = Map.ofEntries(
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("avif", "image/avif"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("txt", "text/plain;charset=UTF-8"),
            Map.entry("csv", "text/csv;charset=UTF-8"),
            Map.entry("json", "application/json")
    );

    private record Source(String repoUrl, String branch, String token, String lastCommit, Timestamp lastSyncTime,
                          Timestamp lastCheckTime, String lastError, String status, int fileCount, int enabled) {
    }

    private record Draft(String id, String title, String content, Timestamp updateTime) {
    }
}
