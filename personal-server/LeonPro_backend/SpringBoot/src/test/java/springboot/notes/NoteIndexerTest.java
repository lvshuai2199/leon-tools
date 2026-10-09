package springboot.notes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoteIndexerTest {

    @Test
    void titleComesFromHeadingOrFrontMatter() {
        assertEquals("技术方案", NoteIndexer.titleOf("a.md", "---\ntitle: x\n---\n\n# 技术方案\n\n正文"));
        assertEquals("README", NoteIndexer.titleOf("README.md", "没有标题\n"));
    }

    @Test
    void skipsDependenciesHiddenFilesAndOutsideLinks(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("repo"));
        Files.writeString(root.resolve("README.md"), "# 欢迎\n");
        Path notes = Files.createDirectories(root.resolve("notes"));
        Files.writeString(notes.resolve("方案.md"), "# 方案\n");
        Path modules = Files.createDirectories(root.resolve("node_modules"));
        Files.writeString(modules.resolve("skip.md"), "# 跳过\n");
        Files.writeString(root.resolve(".secret.md"), "# 隐藏\n");
        Path outside = tmp.resolve("outside.md");
        Files.writeString(outside, "# 外面\n");
        Files.createSymbolicLink(root.resolve("leak.md"), outside);

        NoteIndexer.Result result = NoteIndexer.index(root, 10, 1024);
        assertEquals(2, result.docs().size());
        assertEquals("欢迎", result.docs().stream().filter(d -> d.path().equals("README.md")).findFirst().orElseThrow().title());
        assertEquals("方案", result.docs().stream().filter(d -> d.path().equals("notes/方案.md")).findFirst().orElseThrow().title());
        assertTrue(result.docs().stream().noneMatch(d -> d.path().contains("leak") || d.path().contains("skip")));
    }

    @Test
    void skipsOversizedMarkdown(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("repo"));
        Files.writeString(root.resolve("big.md"), "x".repeat(50));
        Files.writeString(root.resolve("ok.md"), "# ok\n");
        NoteIndexer.Result result = NoteIndexer.index(root, 10, 10);
        assertEquals(1, result.docs().size());
        assertEquals("ok.md", result.docs().get(0).path());
        assertEquals(1, result.skippedLarge());
    }
}
