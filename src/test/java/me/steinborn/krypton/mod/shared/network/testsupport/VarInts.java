package me.steinborn.krypton.mod.shared.network.testsupport;

import io.netty.buffer.ByteBuf;

/** Minimal VarInt helpers so the tests do not depend on Minecraft classes. */
public final class VarInts {
    private VarInts() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void write(ByteBuf buf, int value) {
        while ((value & -128) != 0) {
            buf.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    public static int read(ByteBuf buf) {
        int value = 0;
        int shift = 0;
        byte b;
        do {
            b = buf.readByte();
            value |= (b & 0x7F) << (shift * 7);
            if (++shift > 5) {
                throw new IllegalArgumentException("VarInt too big");
            }
        } while ((b & 0x80) == 0x80);
        return value;
    }

    /** The real number of bytes {@link #write} produces for this value. */
    public static int encodedLength(int value) {
        int length = 1;
        while ((value & -128) != 0) {
            length++;
            value >>>= 7;
        }
        return length;
    }
}
