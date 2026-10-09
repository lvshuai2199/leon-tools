package springboot.service.cursor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CursorKeyCipherTest {

    @Test
    void roundTripAndRejectsOtherSecret() {
        CursorKeyCipher cipher = new CursorKeyCipher("test-secret");
        String packed = cipher.seal("cursor_live_example_key");
        assertFalse(packed.contains("cursor_live_example_key"));
        assertEquals("cursor_live_example_key", cipher.open(packed));
        assertNotEquals(packed, cipher.seal("cursor_live_example_key"));
        assertThrows(IllegalStateException.class, () -> new CursorKeyCipher("other-secret").open(packed));
    }

    @Test
    void blankSecretCannotSeal() {
        assertThrows(IllegalStateException.class, () -> new CursorKeyCipher(" ").seal("cursor_live_example_key"));
    }
}
