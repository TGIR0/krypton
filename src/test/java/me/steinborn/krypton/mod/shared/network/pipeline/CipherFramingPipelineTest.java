package me.steinborn.krypton.mod.shared.network.pipeline;

import com.velocitypowered.natives.util.BufferPreference;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import me.steinborn.krypton.mod.shared.network.testsupport.TestCipher;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A closer imitation of the real server pipeline: encrypted bytes arrive in arbitrary fragments in
 * pooled buffers (like socket reads), get decrypted by {@link MinecraftCipherDecoder}, and are then
 * cut into frames by a {@code ByteToMessageDecoder}. This checks that the decrypted buffers play
 * well with the next handler and that nothing is leaked.
 */
class CipherFramingPipelineTest {
    private static final int KEY = 0x33;

    private static byte[] buildStream(List<byte[]> frames) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        for (byte[] frame : frames) {
            stream.write(frame.length >>> 8);
            stream.write(frame.length & 0xFF);
            stream.writeBytes(frame);
        }
        return stream.toByteArray();
    }

    @Test
    void fragmentedEncryptedStreamIsReassembledIntoTheOriginalFrames() {
        for (BufferPreference preference : BufferPreference.values()) {
            Random random = new Random(preference.ordinal() + 100);
            List<byte[]> frames = new ArrayList<>();
            for (int i = 0; i < 300; i++) {
                byte[] frame = new byte[random.nextInt(2000)];
                random.nextBytes(frame);
                frames.add(frame);
            }
            byte[] stream = buildStream(frames);
            for (int i = 0; i < stream.length; i++) {
                stream[i] ^= KEY;
            }

            EmbeddedChannel channel = new EmbeddedChannel(
                    new MinecraftCipherDecoder(new TestCipher(preference, KEY)),
                    new LengthFieldBasedFrameDecoder(65_535, 0, 2, 0, 2));

            List<ByteBuf> fed = new ArrayList<>();
            List<byte[]> received = new ArrayList<>();
            int offset = 0;
            while (offset < stream.length) {
                int chunk = Math.min(1 + random.nextInt(700), stream.length - offset);
                ByteBuf in = random.nextBoolean()
                        ? PooledByteBufAllocator.DEFAULT.directBuffer(chunk)
                        : PooledByteBufAllocator.DEFAULT.heapBuffer(chunk);
                in.writeBytes(stream, offset, chunk);
                offset += chunk;
                fed.add(in);

                channel.writeInbound(in);
                ByteBuf frame;
                while ((frame = channel.readInbound()) != null) {
                    byte[] bytes = new byte[frame.readableBytes()];
                    frame.readBytes(bytes);
                    frame.release();
                    received.add(bytes);
                }
            }

            assertEquals(frames.size(), received.size(), "frame count for " + preference);
            for (int i = 0; i < frames.size(); i++) {
                assertArrayEquals(frames.get(i), received.get(i), "frame " + i + " for " + preference);
            }
            assertFalse(channel.finish());
            for (int i = 0; i < fed.size(); i++) {
                assertEquals(0, fed.get(i).refCnt(), "input chunk " + i + " leaked for " + preference);
            }
        }
    }
}
