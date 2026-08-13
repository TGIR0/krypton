package me.steinborn.krypton.mod.shared.network.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VarIntUtilTest {

    private static int vanillaGetVarLongLength(long value) {
        for (int i = 1; i < 10; ++i) {
            if ((value & -1L << i * 7) == 0L) {
                return i;
            }
        }
        return 10;
    }

    @Test
    void ensureConsistencyAcrossNumberBitsInt() {
        for (int i = 0; i <= 31; i++) {
            int number = (1 << i) - 1;
            assertEquals(vanillaGetVarLongLength(number), VarIntUtil.getVarIntLength(number),
                    "mismatch with " + i + "-bit number");
        }
    }

    @Test
    void ensureConsistencyForNegativeAndBoundaryValues() {
        int[] testCases = {
                0, 1, 127, 128, 16383, 16384, 2097151, 2097152, 268435455, 268435456,
                Integer.MAX_VALUE, -1, -2, -100, Integer.MIN_VALUE
        };
        for (int value : testCases) {
            assertEquals(vanillaGetVarIntLength(value), VarIntUtil.getVarIntLength(value),
                    "mismatch for value: " + value);
        }
    }

    private static int vanillaGetVarIntLength(int val) {
        for (int i = 1; i < 5; ++i) {
            if ((val & (-1 << (i * 7))) == 0) {
                return i;
            }
        }
        return 5;
    }
}