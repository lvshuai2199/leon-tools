package springboot.service.repo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepoRefsTest {

    @Test
    void acceptsProviderSlugAndRef() {
        assertEquals("github", RepoRefs.provider("GitHub"));
        assertEquals("gitee", RepoRefs.provider(" Gitee "));
        assertEquals("leon-tools", RepoRefs.slug("leon-tools", "仓库名"));
        assertEquals("feature/实验", RepoRefs.ref("feature/实验"));
        assertEquals("abc1234", RepoRefs.sha("ABC1234"));
        assertEquals(1, RepoRefs.page(0));
        assertEquals(10, RepoRefs.page(99));
    }

    @Test
    void rejectsPathTricks() {
        assertThrows(IllegalArgumentException.class, () -> RepoRefs.ref("a/../b"));
        assertThrows(IllegalArgumentException.class, () -> RepoRefs.slug("..", "仓库名"));
        assertThrows(IllegalArgumentException.class, () -> RepoRefs.slug(".", "所有者"));
        assertThrows(IllegalArgumentException.class, () -> RepoRefs.provider("gitlab"));
        assertThrows(IllegalArgumentException.class, () -> RepoRefs.sha("not-a-sha"));
    }
}
