package springboot.notes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotePathsTest {

    @Test
    void normalizesRelativePath() {
        assertEquals("随手记/a.md", NotePaths.normalizeRel("./随手记//a.md"));
    }

    @Test
    void rejectsEscape() {
        assertThrows(IllegalArgumentException.class, () -> NotePaths.normalizeRel("../a.md"));
        assertThrows(IllegalArgumentException.class, () -> NotePaths.normalizeRel("/etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> NotePaths.normalizeRel("a/.git/config"));
        assertThrows(IllegalArgumentException.class, () -> NotePaths.normalizeRel("C:/Windows"));
    }

    @Test
    void symlinkOutsideRepoIsRejected(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("repo");
        Files.createDirectories(root);
        Path outside = tmp.resolve("secret.md");
        Files.writeString(outside, "secret");
        Path link = root.resolve("leak.md");
        Files.createSymbolicLink(link, outside);
        assertThrows(IllegalArgumentException.class, () -> NotePaths.resolve(root, "leak.md"));
    }

    @Test
    void resolvesInsideRepo(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("repo"));
        Files.writeString(root.resolve("a.md"), "hi");
        Path file = NotePaths.resolve(root, "a.md");
        assertTrue(file.startsWith(root.toRealPath()));
        assertEquals("hi", Files.readString(file));
    }
}
