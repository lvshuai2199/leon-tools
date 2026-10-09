package springboot.notes;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoteGitClientTest {

    @Test
    void syncsBranchAndPublishesDraft(@TempDir Path tmp) throws Exception {
        Path seed = tmp.resolve("seed");
        String branch = "main";
        try (Git git = Git.init().setDirectory(seed.toFile()).setInitialBranch(branch).call()) {
            quietSign(git);
            Files.writeString(seed.resolve("README.md"), "# 欢迎\n\n仓库笔记\n");
            git.add().addFilepattern("README.md").call();
            git.commit().setMessage("init").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call();
        }
        Path bare = tmp.resolve("origin.git");
        try (Git git = Git.init().setDirectory(bare.toFile()).setBare(true).setInitialBranch(branch).call()) {
            // 空的 bare 仓库，等 seed 推上来
        }
        String remote = bare.toUri().toString();
        try (Git git = Git.open(seed.toFile())) {
            git.remoteAdd().setName("origin").setUri(new URIish(remote)).call();
            git.push().setRemote("origin").setRefSpecs(new RefSpec("refs/heads/" + branch + ":refs/heads/" + branch)).call();
        }

        NoteGitClient client = new NoteGitClient();
        assertEquals(List.of(branch), client.listBranches(remote, null));
        String first = client.remoteHead(remote, null, branch);

        Path work = tmp.resolve("work");
        String synced = client.sync(work, remote, null, branch, true);
        assertEquals(first, synced);
        assertTrue(Files.readString(work.resolve("README.md")).contains("欢迎"));

        try (Git git = Git.open(seed.toFile())) {
            quietSign(git);
            Files.writeString(seed.resolve("README.md"), "# 欢迎\n\n更新后的正文\n");
            git.add().addFilepattern("README.md").call();
            git.commit().setMessage("update").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call();
            git.push().setRemote("origin").setRefSpecs(new RefSpec("refs/heads/" + branch + ":refs/heads/" + branch)).call();
        }
        String second = client.remoteHead(remote, null, branch);
        assertTrue(!first.equals(second));
        assertEquals(second, client.sync(work, remote, null, branch, false));
        assertTrue(Files.readString(work.resolve("README.md")).contains("更新后的正文"));

        String published = client.publish(work, null, branch, "随手记/2026-10-09_随手.md",
                "# 随手\n\n写完了\n", "随手记: 随手", "LeonPro", "notes@localhost");
        assertTrue(!published.equals(second));
        assertTrue(Files.readString(work.resolve("随手记/2026-10-09_随手.md"), StandardCharsets.UTF_8).contains("写完了"));

        Path check = tmp.resolve("check");
        try (Git git = Git.cloneRepository().setURI(remote).setDirectory(check.toFile()).setBranch(branch).call()) {
            ObjectId head = git.getRepository().resolve("HEAD");
            assertEquals(published, head.getName());
        }
        assertTrue(Files.readString(check.resolve("随手记/2026-10-09_随手.md"), StandardCharsets.UTF_8).contains("写完了"));
    }

    @Test
    void stripsTokenFromError() {
        assertEquals("仓库认证失败。私有仓库或上传时请填写有写入权限的访问令牌",
                NoteGitClient.sanitize("Authentication failed for https://github.com/a/b", "secret-token"));
        assertTrue(!NoteGitClient.sanitize("boom secret-token", "secret-token").contains("secret-token"));
    }

    private static void quietSign(Git git) throws Exception {
        StoredConfig cfg = git.getRepository().getConfig();
        cfg.setString("gpg", null, "format", "openpgp");
        cfg.setBoolean("commit", null, "gpgsign", false);
        cfg.save();
    }
}
