package springboot.service.menu;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuManifestLoaderTest {

    private static MenuManifest parse(String client, String json) {
        return MenuManifestLoader.parse(client, "test", json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void samplesLoad() {
        MenuManifest admin = MenuManifestLoader.load("admin", new ClassPathResource("menu-samples/admin-menus.json"));
        assertEquals(MenuManifest.Status.LOADED, admin.status(), String.valueOf(admin.errors()));
        assertEquals(10, admin.entries().size());
        MenuManifest app = MenuManifestLoader.load("app", new ClassPathResource("menu-samples/app-menus.json"));
        assertEquals(MenuManifest.Status.LOADED, app.status(), String.valueOf(app.errors()));
        assertEquals(4, app.entries().size());
    }

    @Test
    void missingFileIsMissingNotEmpty() {
        MenuManifest m = MenuManifestLoader.load("app", new ClassPathResource("menus/app/does-not-exist.json"));
        assertEquals(MenuManifest.Status.MISSING, m.status());
        assertTrue(m.entries().isEmpty());
    }

    @Test
    void emptyArrayIsLoaded() {
        assertEquals(MenuManifest.Status.LOADED, parse("app", "[]").status());
    }

    @Test
    void invalidJsonOrShape() {
        assertEquals(MenuManifest.Status.INVALID, parse("app", "{").status());
        assertEquals(MenuManifest.Status.INVALID, parse("app", "{\"path\":\"/a\"}").status());
        assertEquals(MenuManifest.Status.INVALID, parse("app", "[{\"path\":\"/a\",\"sort\":\"x\"}]").status());
    }

    @Test
    void validationRules() {
        String ok = "{\"path\":\"/a\",\"name\":\"A\",\"type\":\"page\",\"component\":\"a/index\",\"client\":\"app\"}";
        assertEquals(MenuManifest.Status.LOADED, parse("app", "[" + ok + "]").status());
        // client 与文件不一致
        assertEquals(MenuManifest.Status.INVALID, parse("admin", "[" + ok + "]").status());
        // path 重复（忽略大小写、末尾 /）
        String dup = "{\"path\":\"/A/\",\"name\":\"A2\",\"type\":\"page\",\"component\":\"a/index\",\"client\":\"app\"}";
        assertEquals(MenuManifest.Status.INVALID, parse("app", "[" + ok + "," + dup + "]").status());
        // 不以 / 开头
        assertEquals(MenuManifest.Status.INVALID, parse("app",
                "[{\"path\":\"a\",\"name\":\"A\",\"type\":\"page\",\"component\":\"a\",\"client\":\"app\"}]").status());
        // page 没有 component
        assertEquals(MenuManifest.Status.INVALID, parse("app",
                "[{\"path\":\"/a\",\"name\":\"A\",\"type\":\"page\",\"client\":\"app\"}]").status());
        // dir 的 component 不能是页面
        assertEquals(MenuManifest.Status.INVALID, parse("app",
                "[{\"path\":\"/a\",\"name\":\"A\",\"type\":\"dir\",\"component\":\"a/index\",\"client\":\"app\"}]").status());
        // type 非法、name 缺失
        assertEquals(MenuManifest.Status.INVALID, parse("app",
                "[{\"path\":\"/a\",\"name\":\"A\",\"type\":\"button\",\"client\":\"app\"}]").status());
        assertEquals(MenuManifest.Status.INVALID, parse("app",
                "[{\"path\":\"/a\",\"type\":\"dir\",\"client\":\"app\"}]").status());
        // parent 不存在
        assertEquals(MenuManifest.Status.INVALID, parse("app",
                "[{\"path\":\"/a/b\",\"parent\":\"/a\",\"name\":\"B\",\"type\":\"page\",\"component\":\"b\",\"client\":\"app\"}]").status());
        // parent 成环
        MenuManifest cyc = parse("app", "["
                + "{\"path\":\"/a\",\"parent\":\"/b\",\"name\":\"A\",\"type\":\"dir\",\"client\":\"app\"},"
                + "{\"path\":\"/b\",\"parent\":\"/a\",\"name\":\"B\",\"type\":\"dir\",\"client\":\"app\"}]");
        assertEquals(MenuManifest.Status.INVALID, cyc.status());
        assertTrue(cyc.errors().stream().anyMatch(e -> e.contains("成环")), String.valueOf(cyc.errors()));
    }

    @Test
    void pathsAreNormalizedAndUnknownFieldsIgnored() {
        MenuManifest m = parse("app", "[{\"path\":\" /Tool//x/ \",\"name\":\"X\",\"type\":\"page\","
                + "\"component\":\"x\",\"client\":\"app\",\"extra\":1}]");
        assertEquals(MenuManifest.Status.LOADED, m.status(), String.valueOf(m.errors()));
        assertEquals("/Tool/x", m.entries().get(0).getPath());
    }

    @Test
    void pathHelpers() {
        assertEquals("/a/b", MenuPaths.normalize("a//b/"));
        assertEquals("/", MenuPaths.normalize("/"));
        assertEquals("https://x.com/a", MenuPaths.normalize(" https://x.com/a "));
        assertEquals("/regcode/user", MenuPaths.join("/regcode", "user"));
        assertEquals("user", MenuPaths.menuUrl("/regcode/user", "/regcode"));
        assertEquals("/s/x", MenuPaths.menuUrl("/s/x", "/crab"));
        assertEquals("/tool", MenuPaths.menuUrl("/tool", null));
        assertEquals("ToolWallpaper", MenuPaths.routeName("/tool/wallpaper"));
        assertEquals("CrabId", MenuPaths.routeName("/crab/:id"));
    }
}
