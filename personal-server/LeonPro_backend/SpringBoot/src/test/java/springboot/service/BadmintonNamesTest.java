package springboot.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadmintonNamesTest {

    private static final String D = "2026-10-04";

    @Test
    void emptyNameBecomesDate() {
        assertEquals(D, BadmintonNames.normalize(null, D, null));
        assertEquals(D, BadmintonNames.normalize("   ", D, List.of()));
    }

    @Test
    void appendsDateOnceAndTrims() {
        assertEquals("周末球局 2026-10-04", BadmintonNames.normalize("  周末球局  ", D, null));
        assertEquals("周末球局 2026-10-04", BadmintonNames.normalize("周末球局 2026-10-04", D, null));
        assertEquals("周末球局 2026-10-04", BadmintonNames.normalize("周末球局 2026-10-04 (2)", D, null));
    }

    @Test
    void replacesTrailingDateWhenDateChanges() {
        assertEquals("周末球局 2026-10-05", BadmintonNames.normalize("周末球局 2026-10-04", "2026-10-05", null));
        assertEquals("比赛 2026-10-04", BadmintonNames.normalize("比赛 2026-09-30", D, null));
        assertEquals(D, BadmintonNames.normalize("2026-10-01", D, null));
        assertEquals("羽林 10.1 2026-10-02", BadmintonNames.normalize("羽林 10.1", "2026-10-02", null));
    }

    @Test
    void sameDayDuplicatesGetCounter() {
        Set<String> existing = Set.of("周末球局 2026-10-04", "周末球局 2026-10-04 (2)");
        assertEquals("周末球局 2026-10-04 (3)", BadmintonNames.normalize("周末球局", D, existing));
        assertEquals("2026-10-04 (2)", BadmintonNames.normalize("", D, Set.of(D)));
    }

    @Test
    void longNameIsTruncatedKeepingSuffix() {
        String longName = "长".repeat(150);
        String out = BadmintonNames.normalize(longName, D, Set.of());
        assertEquals(100, out.length());
        assertTrue(out.endsWith(" " + D));
        String out2 = BadmintonNames.normalize(longName, D, Set.of(out));
        assertEquals(100, out2.length());
        assertTrue(out2.endsWith(" " + D + " (2)"));
    }

    @Test
    void endsWithDate() {
        assertTrue(BadmintonNames.endsWithDate("周末球局 2026-10-04 (2)"));
        assertFalse(BadmintonNames.endsWithDate("羽林 10.1"));
    }

    @Test
    void bucketValidation() {
        assertEquals(null, BadmintonBillBizService.validateBucketPrice(null));
        assertEquals(null, BadmintonBillBizService.validateBucketPrice(java.math.BigDecimal.ZERO));
        assertThrows(springboot.utils.BizException.class,
                () -> BadmintonBillBizService.validateBucketPrice(new java.math.BigDecimal("-1")));
        assertThrows(springboot.utils.BizException.class,
                () -> BadmintonBillBizService.validateBucketPrice(new java.math.BigDecimal("1.234")));
        assertThrows(springboot.utils.BizException.class,
                () -> BadmintonBillBizService.validateBucketPrice(new java.math.BigDecimal("100000.01")));
        assertEquals(new java.math.BigDecimal("102.5"),
                BadmintonBillBizService.validateBucketPrice(new java.math.BigDecimal("102.5")));
    }
}
