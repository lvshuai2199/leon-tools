package springboot.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WallpaperSortTest {

    private static Map<String, Integer> sorts(Object... kv) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    @Test
    void reusesExistingValuesInNewOrder() {
        // 页面只加载了 b(20) c(30) d(40)，拖成 d, b, c；a(10) e(50) 未提交
        Map<String, Integer> changes = WallpaperService.reassignSorts(
                List.of("d", "b", "c"), sorts("b", 20, "c", 30, "d", 40));
        assertEquals(Map.of("d", 20, "b", 30, "c", 40), changes);
        assertFalse(changes.containsKey("a"));
        assertFalse(changes.containsKey("e"));
    }

    @Test
    void onlyChangedIdsAreReturned() {
        Map<String, Integer> changes = WallpaperService.reassignSorts(
                List.of("a", "c", "b"), sorts("a", 1, "b", 5, "c", 9));
        assertEquals(Map.of("c", 5, "b", 9), changes);
    }

    @Test
    void duplicatesAreMadeStrictlyIncreasingWithinSubmittedSet() {
        Map<String, Integer> changes = WallpaperService.reassignSorts(
                List.of("c", "a", "b"), sorts("a", 3, "b", 3, "c", 3));
        assertEquals(Map.of("a", 4, "b", 5), changes);
    }

    @Test
    void unknownIdsAndNullSortsAreHandled() {
        Map<String, Integer> changes = WallpaperService.reassignSorts(
                List.of("x", "b", "a"), sorts("a", null, "b", 2));
        // null 视为 0：值 [0,2] 分给 b,a
        assertEquals(Map.of("b", 0, "a", 2), changes);
        assertTrue(!changes.containsKey("x"));
    }
}
