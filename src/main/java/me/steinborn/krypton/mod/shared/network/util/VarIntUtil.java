package me.steinborn.krypton.mod.shared.network.util;

/**
 * Calculates how many bytes a VarInt needs.
 * <p>
 * A VarInt stores 7 bits per byte, so the byte count only depends on how many bits the number
 * uses. {@link Integer#numberOfLeadingZeros(int)} gives that, and a small lookup table, indexed by
 * the number of leading zero bits (0 to 32), turns it into a byte count. A micro-benchmark showed
 * this is faster than computing the length with arithmetic.
 */
public final class VarIntUtil {
    private static final int[] VARINT_EXACT_BYTE_LENGTHS = new int[33];

    static {
        for (int i = 0; i <= 32; ++i) {
            VARINT_EXACT_BYTE_LENGTHS[i] = (int) Math.ceil((31d - (i - 1)) / 7d);
        }
        VARINT_EXACT_BYTE_LENGTHS[32] = 1; // Special case for 0.
    }

    private VarIntUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns the number of bytes needed to encode {@code value} as a VarInt (1 to 5).
     * Negative numbers always need 5 bytes.
     */
    public static int getVarIntLength(int value) {
        return VARINT_EXACT_BYTE_LENGTHS[Integer.numberOfLeadingZeros(value)];
    }
}
