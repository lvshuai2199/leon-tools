package springboot.notes;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoteDraftNamesTest {

    @Test
    void buildsSafeRelativePath() {
        assertEquals("随手记/2026-10-09_会议记录.md",
                NoteDraftNames.relativePath("随手记", " 会议记录 ", LocalDate.of(2026, 10, 9), null));
        assertEquals("随手记/2026-10-09_ab-120501.md",
                NoteDraftNames.relativePath("随手记", "a/b", LocalDate.of(2026, 10, 9), "120501"));
        assertEquals("未命名", NoteDraftNames.sanitizeTitle("   "));
        assertEquals("未命名", NoteDraftNames.sanitizeTitle("/"));
    }
}
