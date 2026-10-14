package me.steinborn.krypton.mod.shared.network.util;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

/**
 * Fast, allocation-free VarInt length calculation, reading, and writing utilities.
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

    /**
     * Reads a standard Minecraft VarInt (up to 5 bytes) directly from {@code buf} without
     * allocating a wrapper object.
     *
     * @throws DecoderException if the VarInt exceeds 5 bytes
     */
    public static int readVarInt(ByteBuf buf) {
        int value = 0;
        int shift = 0;
        byte b;
        do {
            b = buf.readByte();
            value |= (b & 0x7F) << (shift * 7);
            if (++shift > 5) {
                throw new DecoderException("VarInt too big");
            }
        } while ((b & 0x80) != 0);
        return value;
    }

    /**
     * Writes {@code value} as a VarInt into {@code buf}, peeling the common 1-byte and 2-byte
     * cases to improve JIT inlining.
     */
    public static ByteBuf writeVarInt(ByteBuf buf, int value) {
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buf.writeByte(value);
        } else if ((value & (0xFFFFFFFF << 14)) == 0) {
            int w = (value & 0x7F | 0x80) << 8 | (value >>> 7);
            buf.writeShort(w);
        } else {
            writeVarIntFull(buf, value);
        }
        return buf;
    }

    private static void writeVarIntFull(ByteBuf buf, int value) {
        // See https://steinborn.me/posts/performance/how-fast-can-you-write-a-varint/
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buf.writeByte(value);
        } else if ((value & (0xFFFFFFFF << 14)) == 0) {
            int w = (value & 0x7F | 0x80) << 8 | (value >>> 7);
            buf.writeShort(w);
        } else if ((value & (0xFFFFFFFF << 21)) == 0) {
            int w = (value & 0x7F | 0x80) << 16 | ((value >>> 7) & 0x7F | 0x80) << 8 | (value >>> 14);
            buf.writeMedium(w);
        } else if ((value & (0xFFFFFFFF << 28)) == 0) {
            int w = (value & 0x7F | 0x80) << 24 | (((value >>> 7) & 0x7F | 0x80) << 16)
                    | ((value >>> 14) & 0x7F | 0x80) << 8 | (value >>> 21);
            buf.writeInt(w);
        } else {
            int w = (value & 0x7F | 0x80) << 24 | ((value >>> 7) & 0x7F | 0x80) << 16
                    | ((value >>> 14) & 0x7F | 0x80) << 8 | ((value >>> 21) & 0x7F | 0x80);
            buf.writeInt(w);
            buf.writeByte(value >>> 28);
        }
    }
}
