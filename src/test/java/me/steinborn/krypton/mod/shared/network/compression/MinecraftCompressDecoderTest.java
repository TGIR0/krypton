package me.steinborn.krypton.mod.shared.network.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import me.steinborn.krypton.mod.shared.network.testsupport.TestCompressor;
import me.steinborn.krypton.mod.shared.network.testsupport.VarInts;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the decoder through a real Netty {@link EmbeddedChannel}, so reference counting and
 * exception handling behave exactly as they do in a live pipeline.
 */
class MinecraftCompressDecoderTest {
    private static final int THRESHOLD = 256;
    /** The default (vanilla) per-packet cap. Only valid when krypton.permit-oversized-packets is unset. */
    private static final int CAP = 8 * 1024 * 1024;

    private static byte[] zlib(byte[] data) {
        Deflater deflater = new Deflater(4);
        deflater.setInput(data);
        deflater.finish();
        byte[] buffer = new byte[data.length + 1024];
        int length = deflater.deflate(buffer);
        deflater.end();
        return Arrays.copyOf(buffer, length);
    }

    private static ByteBuf packet(int claimedSize, byte[] body) {
        ByteBuf buf = Unpooled.buffer();
        VarInts.write(buf, claimedSize);
        buf.writeBytes(body);
        return buf;
    }

    private static byte[] bytes(ByteBuf buf) {
        byte[] result = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), result);
        return result;
    }

    private static IllegalStateException rejection(EmbeddedChannel channel, ByteBuf packet) {
        DecoderException failure = assertThrows(DecoderException.class, () -> channel.writeInbound(packet));
        return assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    @Test
    void passesThroughPacketsMarkedAsUncompressed() {
        TestCompressor compressor = new TestCompressor();
        EmbeddedChannel channel = new EmbeddedChannel(new MinecraftCompressDecoder(THRESHOLD, true, compressor));
        byte[] payload = new byte[THRESHOLD - 1];
        Arrays.fill(payload, (byte) 7);

        assertTrue(channel.writeInbound(packet(0, payload)));
        ByteBuf out = channel.readInbound();
        assertArrayEquals(payload, bytes(out));
        assertEquals(0, compressor.inflateCalls);
        assertTrue(out.release());
        assertTrue(!channel.finish());
    }

    @Test
    void rejectsUncompressedMarkerWhenPayloadIsAtOrAboveThreshold() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new MinecraftCompressDecoder(THRESHOLD, true, new TestCompressor()));
        ByteBuf in = packet(0, new byte[THRESHOLD]);
        IllegalStateException e = rejection(channel, in);
        assertTrue(e.getMessage().contains("threshold"), e.getMessage());
        assertEquals(0, in.refCnt());
    }

    @Test
    void rejectsNegativeClaimedSizeEvenWhenValidationIsDisabled() {
        TestCompressor compressor = new TestCompressor();
        EmbeddedChannel channel = new EmbeddedChannel(new MinecraftCompressDecoder(THRESHOLD, false, compressor));
        ByteBuf in = packet(-1, new byte[16]);

        IllegalStateException e = rejection(channel, in);
        assertTrue(e.getMessage().contains("negative"), e.getMessage());
        assertEquals(0, compressor.inflateCalls);
        assertEquals(0, in.refCnt());
    }

    @Test
    void rejectsIntegerMinValueClaimedSize() {
        TestCompressor compressor = new TestCompressor();
        EmbeddedChannel channel = new EmbeddedChannel(new MinecraftCompressDecoder(THRESHOLD, false, compressor));
        rejection(channel, packet(Integer.MIN_VALUE, new byte[16]));
        assertEquals(0, compressor.inflateCalls);
    }

    @Test
    void rejectsClaimedSizeAboveCapEvenWhenValidationIsDisabled() {
        for (int claimed : new int[]{CAP + 1, 100_000_000, Integer.MAX_VALUE}) {
            TestCompressor compressor = new TestCompressor();
            EmbeddedChannel channel = new EmbeddedChannel(new MinecraftCompressDecoder(THRESHOLD, false, compressor));
            ByteBuf in = packet(claimed, new byte[100]);

            IllegalStateException e = rejection(channel, in);
            assertTrue(e.getMessage().contains("exceeds"), "claimed=" + claimed + ": " + e.getMessage());
            assertEquals(0, compressor.inflateCalls, "claimed=" + claimed);
            assertEquals(0, in.refCnt(), "claimed=" + claimed);
        }
    }

    @Test
    void acceptsClaimedSizeExactlyAtCap() {
        TestCompressor compressor = new TestCompressor();
        EmbeddedChannel channel = new EmbeddedChannel(new MinecraftCompressDecoder(THRESHOLD, true, compressor));
        byte[] original = new byte[CAP];
        byte[] body = zlib(original);

        assertTrue(channel.writeInbound(packet(CAP, body)));
        ByteBuf out = channel.readInbound();
        assertEquals(CAP, out.readableBytes());
        assertEquals(1, compressor.inflateCalls);
        out.release();
    }

    @Test
    void validationRejectsClaimedSizeBelowThreshold() {
        TestCompressor compressor = new TestCompressor();
        EmbeddedChannel channel = new EmbeddedChannel(new MinecraftCompressDecoder(THRESHOLD, true, compressor));
        byte[] original = new byte[THRESHOLD - 1];

        IllegalStateException e = rejection(channel, packet(original.length, zlib(original)));
        assertTrue(e.getMessage().contains("less than"), e.getMessage());
        assertEquals(0, compressor.inflateCalls);
    }

    @Test
    void withoutValidationSmallClaimedSizeIsStillInflated() {
        TestCompressor compressor = new TestCompressor();
        EmbeddedChannel channel = new EmbeddedChannel(new MinecraftCompressDecoder(THRESHOLD, false, compressor));
        byte[] original = new byte[THRESHOLD - 1];
        Arrays.fill(original, (byte) 3);

        assertTrue(channel.writeInbound(packet(original.length, zlib(original))));
        ByteBuf out = channel.readInbound();
        assertArrayEquals(original, bytes(out));
        out.release();
    }

    @Test
    void inflatesValidCompressedPacket() {
        TestCompressor compressor = new TestCompressor();
        EmbeddedChannel channel = new EmbeddedChannel(new MinecraftCompressDecoder(THRESHOLD, true, compressor));
        byte[] original = new byte[5000];
        for (int i = 0; i < original.length; i++) {
            original[i] = (byte) (i % 31);
        }

        ByteBuf in = packet(original.length, zlib(original));
        assertTrue(channel.writeInbound(in));
        ByteBuf out = channel.readInbound();
        assertArrayEquals(original, bytes(out));
        assertEquals(0, in.refCnt(), "input buffer must be released");
        assertTrue(out.release());
        assertNull(channel.readInbound());
    }

    @Test
    void corruptDataFailsCleanlyWithoutLeakingBuffers() {
        TestCompressor compressor = new TestCompressor();
        EmbeddedChannel channel = new EmbeddedChannel(new MinecraftCompressDecoder(THRESHOLD, true, compressor));
        byte[] garbage = new byte[64];
        Arrays.fill(garbage, (byte) 0x55);

        ByteBuf in = packet(1000, garbage);
        DecoderException failure = assertThrows(DecoderException.class, () -> channel.writeInbound(in));
        assertNotNull(failure.getCause());
        assertEquals(1, compressor.inflateCalls);
        assertEquals(0, in.refCnt(), "input buffer must be released after a failure");
    }

    @Test
    void claimedSizeLargerThanActualDataFails() {
        TestCompressor compressor = new TestCompressor();
        EmbeddedChannel channel = new EmbeddedChannel(new MinecraftCompressDecoder(THRESHOLD, true, compressor));
        byte[] original = new byte[1000];

        ByteBuf in = packet(2000, zlib(original));
        assertThrows(DecoderException.class, () -> channel.writeInbound(in));
        assertEquals(0, in.refCnt());
    }

    @Test
    void updatedThresholdAppliesToLaterPackets() {
        MinecraftCompressDecoder decoder = new MinecraftCompressDecoder(THRESHOLD, true, new TestCompressor());
        EmbeddedChannel channel = new EmbeddedChannel(decoder);
        byte[] payload = new byte[THRESHOLD + 10];

        // With the original threshold this payload is too big to be sent uncompressed.
        rejection(channel, packet(0, payload));

        // After the server raises the threshold, the same payload is fine.
        decoder.setThreshold(THRESHOLD * 4);
        assertTrue(channel.writeInbound(packet(0, payload)));
        ((ByteBuf) channel.readInbound()).release();
    }

    @Test
    void closesCompressorWhenHandlerIsRemoved() {
        TestCompressor compressor = new TestCompressor();
        EmbeddedChannel channel = new EmbeddedChannel(new MinecraftCompressDecoder(THRESHOLD, true, compressor));
        channel.finish();
        assertEquals(1, compressor.closeCalls);
    }
}
