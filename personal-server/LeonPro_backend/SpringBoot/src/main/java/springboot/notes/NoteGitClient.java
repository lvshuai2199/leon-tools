package springboot.notes;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 用 JGit 读远程分支、浅克隆、拉取，以及把一篇随手记提交并推回去。
 * 令牌只放在内存里的 CredentialsProvider，不写进 remote url。
 */
@Component
public class NoteGitClient {

    public List<String> listBranches(String repoUrl, String token) {
        try {
            Collection<Ref> refs = lsRemote(repoUrl, token);
            List<String> names = new ArrayList<>();
            for (Ref ref : refs) {
                String name = ref.getName();
                if (name != null && name.startsWith("refs/heads/")) {
                    names.add(name.substring("refs/heads/".length()));
                }
            }
            names.sort(String.CASE_INSENSITIVE_ORDER);
            return names;
        } catch (NoteSyncException e) {
            throw e;
        } catch (Exception e) {
            throw new NoteSyncException(publicMessage(e, token));
        }
    }

    /** 远程分支指向的提交；分支不存在时返回 null。 */
    public String remoteHead(String repoUrl, String token, String branch) {
        try {
            String want = "refs/heads/" + branch;
            for (Ref ref : lsRemote(repoUrl, token)) {
                if (want.equals(ref.getName()) && ref.getObjectId() != null) {
                    return ref.getObjectId().getName();
                }
            }
            return null;
        } catch (NoteSyncException e) {
            throw e;
        } catch (Exception e) {
            throw new NoteSyncException(publicMessage(e, token));
        }
    }

    /**
     * @param clean true 时删掉本地目录重新克隆（换了地址或分支）
     * @return 当前 HEAD
     */
    public String sync(Path workDir, String repoUrl, String token, String branch, boolean clean) {
        try {
            if (clean) {
                deleteRecursive(workDir);
            }
            if (!Files.isDirectory(workDir.resolve(".git"))) {
                Files.createDirectories(workDir.getParent() == null ? workDir : workDir.getParent());
                var clone = Git.cloneRepository()
                        .setURI(repoUrl)
                        .setDirectory(workDir.toFile())
                        .setCloneAllBranches(false)
                        .setBranchesToClone(List.of("refs/heads/" + branch))
                        .setBranch(branch)
                        .setDepth(1)
                        .setTimeout(60);
                UsernamePasswordCredentialsProvider creds = credentials(token);
                if (creds != null) {
                    clone.setCredentialsProvider(creds);
                }
                try (Git git = clone.call()) {
                    disableAutocrlf(git);
                    return head(git);
                }
            }
            try (Git git = Git.open(workDir.toFile())) {
                disableAutocrlf(git);
                StoredConfig cfg = git.getRepository().getConfig();
                cfg.setString("remote", "origin", "url", repoUrl);
                cfg.save();
                var fetch = git.fetch()
                        .setRemote("origin")
                        .setRefSpecs(new RefSpec("+refs/heads/" + branch + ":refs/remotes/origin/" + branch))
                        .setDepth(1)
                        .setTimeout(60);
                UsernamePasswordCredentialsProvider creds = credentials(token);
                if (creds != null) {
                    fetch.setCredentialsProvider(creds);
                }
                fetch.call();
                git.reset()
                        .setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD)
                        .setRef("refs/remotes/origin/" + branch)
                        .call();
                return head(git);
            }
        } catch (NoteSyncException e) {
            throw e;
        } catch (Exception e) {
            throw new NoteSyncException(publicMessage(e, token));
        }
    }

    /**
     * 把一篇 Markdown 提交并推到当前分支。失败时把工作区重置回推送前的 HEAD，正文仍留在随手记缓存里。
     */
    public String publish(Path workDir, String token, String branch, String relativePath, String content,
                          String message, String authorName, String authorEmail) {
        String rel = NotePaths.normalizeRel(relativePath);
        try (Git git = Git.open(workDir.toFile())) {
            disableAutocrlf(git);
            String prev = head(git);
            Path file = NotePaths.resolve(workDir, rel);
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, content, StandardCharsets.UTF_8);
            try {
                git.add().addFilepattern(rel).call();
                git.commit()
                        .setMessage(message)
                        .setAuthor(authorName, authorEmail)
                        .setCommitter(authorName, authorEmail)
                        .call();
                var push = git.push()
                        .setRemote("origin")
                        .setRefSpecs(new RefSpec("HEAD:refs/heads/" + branch))
                        .setTimeout(60);
                UsernamePasswordCredentialsProvider creds = credentials(token);
                if (creds != null) {
                    push.setCredentialsProvider(creds);
                }
                StringBuilder fail = new StringBuilder();
                for (var result : push.call()) {
                    for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                        RemoteRefUpdate.Status status = update.getStatus();
                        if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE) {
                            if (fail.length() > 0) {
                                fail.append('；');
                            }
                            fail.append(status.name());
                            if (update.getMessage() != null && !update.getMessage().isBlank()) {
                                fail.append(' ').append(update.getMessage());
                            }
                        }
                    }
                }
                if (fail.length() > 0) {
                    throw new NoteSyncException("上传被远程拒绝：" + sanitize(fail.toString(), token));
                }
                return head(git);
            } catch (Exception e) {
                rollback(git, prev);
                if (e instanceof NoteSyncException nse) {
                    throw nse;
                }
                throw new NoteSyncException(publicMessage(e, token));
            }
        } catch (NoteSyncException e) {
            throw e;
        } catch (Exception e) {
            throw new NoteSyncException(publicMessage(e, token));
        }
    }

    public static String publicMessage(Throwable e, String token) {
        Throwable cur = e;
        String msg = null;
        while (cur != null) {
            if (cur.getMessage() != null && !cur.getMessage().isBlank()) {
                msg = cur.getMessage();
            }
            cur = cur.getCause();
        }
        if (msg == null || msg.isBlank()) {
            msg = "仓库访问失败";
        }
        return sanitize(msg, token);
    }

    static String sanitize(String msg, String token) {
        String m = msg == null ? "" : msg;
        if (token != null && !token.isBlank()) {
            m = m.replace(token, "***");
        }
        m = m.replaceAll("(?i)(https?://)[^/\\s@]+@", "$1");
        String lower = m.toLowerCase(Locale.ROOT);
        if (lower.contains("not authorized") || lower.contains("authentication is required")
                || lower.contains("authentication failed") || lower.contains("401") || lower.contains("403")) {
            return "仓库认证失败。私有仓库或上传时请填写有写入权限的访问令牌";
        }
        if (lower.contains("not found") || lower.contains("repository not found") || lower.contains("404")) {
            return "仓库不存在，或当前令牌没有访问权限";
        }
        if (lower.contains("unknownhost") || lower.contains("unknown host") || lower.contains("unable to resolve")) {
            return "无法解析仓库地址";
        }
        if (m.length() > 400) {
            m = m.substring(0, 400);
        }
        return m.isBlank() ? "仓库访问失败" : m;
    }

    private static Collection<Ref> lsRemote(String repoUrl, String token) throws GitAPIException {
        var cmd = Git.lsRemoteRepository().setRemote(repoUrl).setHeads(true).setTimeout(20);
        UsernamePasswordCredentialsProvider creds = credentials(token);
        if (creds != null) {
            cmd.setCredentialsProvider(creds);
        }
        return cmd.call();
    }

    private static UsernamePasswordCredentialsProvider credentials(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return new UsernamePasswordCredentialsProvider("git", token);
    }

    private static void disableAutocrlf(Git git) throws IOException {
        StoredConfig cfg = git.getRepository().getConfig();
        cfg.setString("core", null, "autocrlf", "false");
        // 宿主机 ~/.gitconfig 里若是 gpg.format=ssh，旧版 JGit 会在 commit 时直接报错
        cfg.setString("gpg", null, "format", "openpgp");
        cfg.setBoolean("commit", null, "gpgsign", false);
        cfg.save();
    }

    private static String head(Git git) throws IOException {
        ObjectId head = git.getRepository().resolve("HEAD");
        if (head == null) {
            throw new NoteSyncException("仓库里没有可用的提交");
        }
        return head.getName();
    }

    private static void rollback(Git git, String prev) {
        if (prev == null || prev.isBlank()) {
            return;
        }
        try {
            git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).setRef(prev).call();
        } catch (Exception ignored) {
            // 下次同步会按远程提交再重置一次
        }
    }

    static void deleteRecursive(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
