package me.steinborn.krypton.mod.shared.network.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompressionLevelTest {
    @Test
    void usesTheDefaultWhenNothingIsConfigured() {
        assertEquals(4, CompressionLevel.DEFAULT);
        assertEquals(CompressionLevel.DEFAULT, CompressionLevel.resolve(null));
    }

    @Test
    void acceptsEveryLevelInTheSupportedRange() {
        for (int level = CompressionLevel.MIN; level <= CompressionLevel.MAX; level++) {
            assertEquals(level, CompressionLevel.resolve(Integer.toString(level)), "level " + level);
        }
    }

    @Test
    void toleratesSurroundingWhitespace() {
        assertEquals(6, CompressionLevel.resolve(" 6 "));
    }

    @Test
    void fallsBackToTheDefaultForInvalidOrOutOfRangeValues() {
        for (String raw : new String[]{"", " ", "abc", "4.5", "0", "10", "-1", "99999999999", "٤"}) {
            assertEquals(CompressionLevel.DEFAULT, CompressionLevel.resolve(raw), "input '" + raw + "'");
        }
    }

    @Test
    void theActiveLevelIsAlwaysValid() {
        int level = CompressionLevel.get();
        assertTrue(level >= CompressionLevel.MIN && level <= CompressionLevel.MAX, "level " + level);
    }
}
