package me.steinborn.krypton.mod.shared.network.compression;

import com.velocitypowered.natives.util.BufferPreference;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import me.steinborn.krypton.mod.shared.network.testsupport.TestCompressor;
import me.steinborn.krypton.mod.shared.network.testsupport.VarInts;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Encoder and decoder wired together: whatever goes in must come out unchanged. */
class CompressionRoundTripTest {
    private static final int THRESHOLD = 256;

    private static byte[] compressibleData(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (i % 13);
        }
        return data;
    }

    private static byte[] randomData(int length, long seed) {
        byte[] data = new byte[length];
        new Random(seed).nextBytes(data);
        return data;
    }

    private static byte[] roundTrip(byte[] original, BufferPreference preference, boolean directInput) {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                new MinecraftCompressEncoder(THRESHOLD, new TestCompressor(preference)));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(
                new MinecraftCompressDecoder(THRESHOLD, true, new TestCompressor(preference)));

        ByteBuf in = directInput ? Unpooled.directBuffer() : Unpooled.buffer();
        in.writeBytes(original);

        assertTrue(encoderChannel.writeOutbound(in));
        ByteBuf encoded = encoderChannel.readOutbound();

        int marker = VarInts.read(encoded.duplicate());
        assertEquals(original.length < THRESHOLD ? 0 : original.length, marker,
                "length marker for " + original.length + " bytes");

        assertTrue(decoderChannel.writeInbound(encoded));
        ByteBuf decoded = decoderChannel.readInbound();
        byte[] result = new byte[decoded.readableBytes()];
        decoded.readBytes(result);

        assertTrue(decoded.release(), "decoded buffer should be fully released by its owner");
        assertEquals(0, in.refCnt(), "encoder input must be released");
        assertEquals(0, encoded.refCnt(), "encoded buffer must be released by the decoder");
        assertNull(encoderChannel.readOutbound());
        assertNull(decoderChannel.readInbound());
        return result;
    }

    @Test
    void roundTripsAcrossSizesAroundTheThreshold() {
        int[] sizes = {0, 1, 2, THRESHOLD - 1, THRESHOLD, THRESHOLD + 1, 1000, 4096, 100_000, 1_000_000};
        for (BufferPreference preference : BufferPreference.values()) {
            for (boolean direct : new boolean[]{false, true}) {
                for (int size : sizes) {
                    byte[] compressible = compressibleData(size);
                    assertArrayEquals(compressible, roundTrip(compressible, preference, direct),
                            "compressible " + size + " " + preference + " direct=" + direct);

                    byte[] random = randomData(size, size * 31L + 7);
                    assertArrayEquals(random, roundTrip(random, preference, direct),
                            "random " + size + " " + preference + " direct=" + direct);
                }
            }
        }
    }

    @Test
    void packetsBelowThresholdAreNotCompressed() {
        TestCompressor compressor = new TestCompressor();
        EmbeddedChannel channel = new EmbeddedChannel(new MinecraftCompressEncoder(THRESHOLD, compressor));
        byte[] original = compressibleData(THRESHOLD - 1);

        channel.writeOutbound(Unpooled.wrappedBuffer(original));
        ByteBuf encoded = channel.readOutbound();

        assertEquals(0, compressor.deflateCalls);
        assertEquals(0, VarInts.read(encoded));
        assertEquals(original.length, encoded.readableBytes());
        encoded.release();
    }

    @Test
    void packetsAtThresholdAreCompressed() {
        TestCompressor compressor = new TestCompressor();
        EmbeddedChannel channel = new EmbeddedChannel(new MinecraftCompressEncoder(THRESHOLD, compressor));
        byte[] original = compressibleData(THRESHOLD);

        channel.writeOutbound(Unpooled.wrappedBuffer(original));
        ByteBuf encoded = channel.readOutbound();

        assertEquals(1, compressor.deflateCalls);
        assertEquals(THRESHOLD, VarInts.read(encoded));
        assertTrue(encoded.readableBytes() < original.length, "compressible data should shrink");
        encoded.release();
    }

    @Test
    void encoderUsesUpdatedThreshold() {
        MinecraftCompressEncoder encoder = new MinecraftCompressEncoder(THRESHOLD, new TestCompressor());
        EmbeddedChannel channel = new EmbeddedChannel(encoder);
        encoder.setThreshold(5000);

        channel.writeOutbound(Unpooled.wrappedBuffer(compressibleData(1000)));
        ByteBuf encoded = channel.readOutbound();
        assertEquals(0, VarInts.read(encoded), "1000 bytes is below the new threshold of 5000");
        encoded.release();
    }

    @Test
    void manyPacketsInARowStayIntact() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                new MinecraftCompressEncoder(THRESHOLD, new TestCompressor()));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(
                new MinecraftCompressDecoder(THRESHOLD, true, new TestCompressor()));
        Random random = new Random(1234);

        for (int i = 0; i < 500; i++) {
            byte[] original = (i % 2 == 0)
                    ? compressibleData(random.nextInt(3000))
                    : randomData(random.nextInt(3000), i);
            encoderChannel.writeOutbound(Unpooled.wrappedBuffer(original));
            ByteBuf encoded = encoderChannel.readOutbound();
            decoderChannel.writeInbound(encoded);
            ByteBuf decoded = decoderChannel.readInbound();
            byte[] result = new byte[decoded.readableBytes()];
            decoded.readBytes(result);
            decoded.release();
            assertArrayEquals(original, result, "packet #" + i);
        }
    }

    @Test
    void handlersCloseTheirCompressorWhenRemoved() {
        TestCompressor encoderCompressor = new TestCompressor();
        TestCompressor decoderCompressor = new TestCompressor();
        new EmbeddedChannel(new MinecraftCompressEncoder(THRESHOLD, encoderCompressor)).finish();
        new EmbeddedChannel(new MinecraftCompressDecoder(THRESHOLD, true, decoderCompressor)).finish();
        assertEquals(1, encoderCompressor.closeCalls);
        assertEquals(1, decoderCompressor.closeCalls);
    }
}
