package me.steinborn.krypton.mod.shared.network.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import me.steinborn.krypton.mod.shared.network.testsupport.VarInts;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VarIntUtilTest {

    /** The length calculation used by vanilla Minecraft. This is the reference behaviour. */
    private static int vanillaGetVarIntLength(int value) {
        for (int i = 1; i < 5; ++i) {
            if ((value & (-1 << (i * 7))) == 0) {
                return i;
            }
        }
        return 5;
    }

    @Test
    void matchesVanillaForEveryBitLength() {
        for (int bits = 0; bits <= 31; bits++) {
            int number = (1 << bits) - 1;
            assertEquals(vanillaGetVarIntLength(number), VarIntUtil.getVarIntLength(number),
                    "mismatch with " + bits + "-bit number");
        }
    }

    @Test
    void matchesVanillaAtEveryVarIntBoundary() {
        // A VarInt grows by one byte every 7 bits, so test just below, at, and just above each limit.
        for (int bits = 7; bits <= 28; bits += 7) {
            int limit = 1 << bits;
            for (int value : new int[]{limit - 2, limit - 1, limit, limit + 1}) {
                assertEquals(vanillaGetVarIntLength(value), VarIntUtil.getVarIntLength(value), "value " + value);
            }
        }
    }

    @Test
    void matchesVanillaForNegativeAndExtremeValues() {
        int[] values = {0, 1, 127, 128, Integer.MAX_VALUE, -1, -2, -100, -128, Integer.MIN_VALUE,
                Integer.MIN_VALUE + 1};
        for (int value : values) {
            assertEquals(vanillaGetVarIntLength(value), VarIntUtil.getVarIntLength(value), "value " + value);
        }
    }

    @Test
    void matchesTheRealEncodedLengthAndRoundTrips() {
        // Compare against the bytes an actual VarInt encoder writes, not just another formula.
        Random random = new Random(42);
        int[] fixed = {0, 1, 127, 128, 16383, 16384, 2097151, 2097152, 268435455, 268435456,
                Integer.MAX_VALUE, -1, Integer.MIN_VALUE};
        for (int value : fixed) {
            assertEncodedLengthMatches(value);
        }
        for (int i = 0; i < 100_000; i++) {
            assertEncodedLengthMatches(random.nextInt());
        }
    }

    private static void assertEncodedLengthMatches(int value) {
        ByteBuf referenceBuf = Unpooled.buffer(5);
        ByteBuf fastBuf = Unpooled.buffer(5);
        try {
            VarInts.write(referenceBuf, value);
            VarIntUtil.writeVarInt(fastBuf, value);

            int written = referenceBuf.readableBytes();
            assertEquals(written, VarIntUtil.getVarIntLength(value), "value " + value);
            assertEquals(written, VarInts.encodedLength(value), "helper, value " + value);
            assertEquals(referenceBuf, fastBuf, "encoded bytes for value " + value);
            assertEquals(value, VarIntUtil.readVarInt(fastBuf), "decoded value for " + value);
        } finally {
            referenceBuf.release();
            fastBuf.release();
        }
    }

    @Test
    void readVarIntRejectsOverlongSixByteSequence() {
        ByteBuf overlong = Unpooled.wrappedBuffer(new byte[]{
                (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01
        });
        try {
            assertThrows(DecoderException.class, () -> VarIntUtil.readVarInt(overlong));
        } finally {
            overlong.release();
        }
    }

    @Test
    void matchesVanillaForEveryPossibleInt() {
        // All 2^32 values, split into 65,536 chunks of 65,536 so it can run in parallel.
        long mismatches = IntStream.range(0, 1 << 16).parallel().mapToLong(high -> {
            long bad = 0;
            int base = high << 16;
            for (int low = 0; low < (1 << 16); low++) {
                int value = base | low;
                if (VarIntUtil.getVarIntLength(value) != vanillaGetVarIntLength(value)) {
                    bad++;
                }
            }
            return bad;
        }).sum();
        assertEquals(0, mismatches, "number of ints whose length differs from vanilla");
    }
}
