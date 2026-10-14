package me.steinborn.krypton.mod.shared.network.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.EncoderException;
import me.steinborn.krypton.mod.shared.network.util.PacketFraming;
import me.steinborn.krypton.mod.shared.network.util.VarIntUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftVarintPrependerTest {
    @Test
    void prependsExactVarIntLengthAndPreservesPayload() {
        for (int size : new int[]{0, 1, 127, 128, 16_383, 16_384, 65_535}) {
            EmbeddedChannel channel = new EmbeddedChannel(MinecraftVarintPrepender.INSTANCE);
            ByteBuf payload = Unpooled.buffer(size);
            payload.writeZero(size);

            assertTrue(channel.writeOutbound(payload));

            ByteBuf lengthPrefix = channel.readOutbound();
            ByteBuf forwardedPayload = channel.readOutbound();
            assertNull(channel.readOutbound());

            assertEquals(VarIntUtil.getVarIntLength(size), lengthPrefix.readableBytes());
            assertEquals(size, VarIntUtil.readVarInt(lengthPrefix));
            assertEquals(size, forwardedPayload.readableBytes());

            assertTrue(lengthPrefix.release());
            assertTrue(forwardedPayload.release());
            assertEquals(0, payload.refCnt());
        }
    }

    @Test
    void rejectsFrameExceedingThreeByteVarIntLimit() {
        EmbeddedChannel channel = new EmbeddedChannel(MinecraftVarintPrepender.INSTANCE);
        ByteBuf oversized = Unpooled.buffer(0) {
            @Override
            public int readableBytes() {
                return PacketFraming.MAX_FRAME_LENGTH + 1;
            }
        };

        assertThrows(EncoderException.class, () -> channel.writeOutbound(oversized));
        assertEquals(0, oversized.refCnt());
    }
}
