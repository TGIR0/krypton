package me.steinborn.krypton.mod.shared.network.compression;

import com.velocitypowered.natives.compression.VelocityCompressor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MinecraftCompressDecoderTest {
    @Test
    void rejectsNegativeClaimedSizeEvenWhenValidationIsDisabled() throws Exception {
        VelocityCompressor compressor = mock(VelocityCompressor.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        MinecraftCompressDecoder decoder = new MinecraftCompressDecoder(256, false, compressor);

        ByteBuf in = Unpooled.buffer();
        try {
            // VarInt encoding of -1.
            in.writeByte(0xFF);
            in.writeByte(0xFF);
            in.writeByte(0xFF);
            in.writeByte(0xFF);
            in.writeByte(0x0F);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> decoder.decode(ctx, in, new ArrayList<>())
            );

            assertTrue(exception.getMessage().contains("must not be negative"));
            verifyNoInteractions(compressor);
        } finally {
            in.release();
            Mockito.clearInvocations(compressor, ctx);
        }
    }
}
