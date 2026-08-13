package me.steinborn.krypton.mod.shared.network.util;

/**
 * Utility class for computing the byte length of a Minecraft VarInt.
 *
 * <p>This implementation is fully branchless, allocation-free, and deterministic.
 * It does not rely on branch prediction, data distribution assumptions, or
 * JIT bounds-check elimination. The cost is constant regardless of input value.</p>
 *
 * <p>The formula is derived from Google Protobuf's {@code computeUInt32SizeNoTag}:
 * we count the number of 7-bit chunks required to represent the value by
 * approximating division by 7 with multiplication by 9/64. Multiplying by 9
 * compiles to a single {@code LEA} instruction on x86 ({@code leal (%rax,%rax,8), %eax})
 * or a single shifted-add on ARM ({@code add w0, w0, w0, lsl #3}). Dividing by 64
 * is a single 6-bit right shift.</p>
 *
 * <p>Derivation:</p>
 * <pre>
 *   floor(((32 - clz) / 7.111...) + 1)
 * = ((32 - clz) * 9) / 64 + 1
 * = (((32 - clz) * 9) >>> 6) + 1
 * = ((32 * 9 + 64 - clz * 9) >>> 6)
 * = ((352 - clz * 9) >>> 6)
 * </pre>
 *
 * @see <a href="https://github.com/protocolbuffers/protobuf/blob/main/java/core/src/main/java/com/google/protobuf/CodedOutputStream.java">Protobuf CodedOutputStream</a>
 * @see <a href="https://steinborn.me/posts/performance/how-fast-can-you-write-a-varint/">How fast can you write a VarInt?</a>
 */
public final class VarIntUtil {

    private VarIntUtil() {
        // Utility class — prevent instantiation
    }

    /**
     * Returns the number of bytes required to encode the given integer
     * as a Minecraft VarInt.
     *
     * <p>Negative numbers always require 5 bytes, since Minecraft VarInt
     * uses 5-byte encoding for negative values (they are sign-extended
     * to 32 bits in the protocol).</p>
     *
     * @param value the integer value to measure
     * @return the byte length, always in the range {@code [1, 5]}
     */
    public static int getVarIntLength(int value) {
        return (352 - (Integer.numberOfLeadingZeros(value) * 9)) >>> 6;
    }
}