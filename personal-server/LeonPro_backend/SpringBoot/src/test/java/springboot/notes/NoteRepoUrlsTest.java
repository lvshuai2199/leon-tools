package springboot.notes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoteRepoUrlsTest {

    @Test
    void acceptsHttpsAndStripsTrailingSlash() {
        assertEquals("https://github.com/shuai/notes.git",
                NoteRepoUrls.normalizeRepoUrl(" https://github.com/shuai/notes.git/ "));
        assertEquals("http://192.168.1.8/git/notes",
                NoteRepoUrls.normalizeRepoUrl("http://192.168.1.8/git/notes"));
    }

    @Test
    void rejectsUnsafeUrls() {
        assertThrows(IllegalArgumentException.class, () -> NoteRepoUrls.normalizeRepoUrl(""));
        assertThrows(IllegalArgumentException.class, () -> NoteRepoUrls.normalizeRepoUrl("file:///tmp/repo"));
        assertThrows(IllegalArgumentException.class, () -> NoteRepoUrls.normalizeRepoUrl("git@github.com:a/b.git"));
        assertThrows(IllegalArgumentException.class,
                () -> NoteRepoUrls.normalizeRepoUrl("https://user:token@github.com/a/b.git"));
        assertThrows(IllegalArgumentException.class, () -> NoteRepoUrls.normalizeRepoUrl("https://github.com"));
        assertThrows(IllegalArgumentException.class,
                () -> NoteRepoUrls.normalizeRepoUrl("https://169.254.169.254/latest/meta-data"));
    }

    @Test
    void branchAllowsUnicodeAndRejectsTraversal() {
        assertEquals("main", NoteRepoUrls.normalizeBranch(" main "));
        assertEquals("feature/笔记", NoteRepoUrls.normalizeBranch("feature/笔记"));
        assertThrows(IllegalArgumentException.class, () -> NoteRepoUrls.normalizeBranch(""));
        assertThrows(IllegalArgumentException.class, () -> NoteRepoUrls.normalizeBranch("../main"));
        assertThrows(IllegalArgumentException.class, () -> NoteRepoUrls.normalizeBranch("-main"));
        assertThrows(IllegalArgumentException.class, () -> NoteRepoUrls.normalizeBranch("a b"));
    }

    @Test
    void tokenRejectsWhitespace() {
        assertEquals("", NoteRepoUrls.normalizeToken("  "));
        assertEquals("ghp_abc", NoteRepoUrls.normalizeToken(" ghp_abc "));
        assertThrows(IllegalArgumentException.class, () -> NoteRepoUrls.normalizeToken("a b"));
    }
}
