package me.steinborn.krypton.mod.shared.network.util;

import io.netty.handler.codec.EncoderException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketFramingTest {
    @Test
    void acceptsLengthsUpToTheMaximum() {
        for (int length : new int[]{0, 1, 127, 128, 16_383, 16_384, 1_000_000, PacketFraming.MAX_FRAME_LENGTH}) {
            assertDoesNotThrow(() -> PacketFraming.checkFrameLength(length), "length " + length);
        }
    }

    @Test
    void rejectsLengthsAboveTheMaximum() {
        for (int length : new int[]{PacketFraming.MAX_FRAME_LENGTH + 1, 3_000_000, 100_000_000, Integer.MAX_VALUE}) {
            EncoderException e = assertThrows(EncoderException.class,
                    () -> PacketFraming.checkFrameLength(length), "length " + length);
            assertTrue(e.getMessage().contains(Integer.toString(length)), e.getMessage());
        }
    }

    @Test
    void rejectsNegativeLengths() {
        assertThrows(EncoderException.class, () -> PacketFraming.checkFrameLength(-1));
        assertThrows(EncoderException.class, () -> PacketFraming.checkFrameLength(Integer.MIN_VALUE));
    }

    @Test
    void limitMatchesTheThreeByteVarIntBoundaryExactly() {
        assertEquals(2_097_151, PacketFraming.MAX_FRAME_LENGTH);
        // The constant must agree with the real VarInt size: a length is accepted if and only if its
        // VarInt prefix is at most 3 bytes. Check every value around the boundary and a wide sweep.
        for (int length = PacketFraming.MAX_FRAME_LENGTH - 5; length <= PacketFraming.MAX_FRAME_LENGTH + 5; length++) {
            assertEquals(VarIntUtil.getVarIntLength(length) <= 3, accepted(length), "length " + length);
        }
        for (int length = 0; length < 4_200_000; length += 7) {
            assertEquals(VarIntUtil.getVarIntLength(length) <= 3, accepted(length), "length " + length);
        }
    }

    private static boolean accepted(int length) {
        try {
            PacketFraming.checkFrameLength(length);
            return true;
        } catch (EncoderException e) {
            return false;
        }
    }
}
