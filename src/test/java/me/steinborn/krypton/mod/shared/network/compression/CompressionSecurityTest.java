package me.steinborn.krypton.mod.shared.network.compression;

import com.velocitypowered.natives.compression.VelocityCompressor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CompressionSecurityTest {

    @Test
    void zipBombProtection() throws Exception {
        ByteBuf maliciousPacket = Unpooled.buffer();
        FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(maliciousPacket);
        friendlyBuf.writeVarInt(Integer.MAX_VALUE); // claimed size = 2GB
        friendlyBuf.writeBytes(new byte[100]); // tiny actual data

        VelocityCompressor mockCompressor = Mockito.mock(VelocityCompressor.class);
        ChannelHandlerContext mockCtx = Mockito.mock(ChannelHandlerContext.class);
        
        MinecraftCompressDecoder decoder = new MinecraftCompressDecoder(256, false, mockCompressor);
        List<Object> out = new ArrayList<>();

        assertThrows(IllegalStateException.class, () -> {
            decoder.decode(mockCtx, maliciousPacket, out);
        }, "Should reject oversized claimed length even when validate=false");
        
        maliciousPacket.release();
    }
}