package me.steinborn.krypton.mod.shared.network.pipeline;

import com.velocitypowered.natives.util.BufferPreference;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import me.steinborn.krypton.mod.shared.network.testsupport.TestCipher;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the encryption handlers for correct output and, most importantly, correct reference
 * counting: every buffer must end up released exactly once, for every buffer type a native
 * implementation can ask for.
 */
class CipherPipelineTest {
    private static final int KEY = 0x5A;

    private static byte[] sample(int length) {
        byte[] data = new byte[length];
        new Random(length).nextBytes(data);
        return data;
    }

    private static ByteBuf buffer(byte[] data, boolean direct) {
        ByteBuf buf = direct ? Unpooled.directBuffer(data.length) : Unpooled.buffer(data.length);
        buf.writeBytes(data);
        return buf;
    }

    private static byte[] drain(ByteBuf buf) {
        byte[] result = new byte[buf.readableBytes()];
        buf.readBytes(result);
        return result;
    }

    @Test
    void decoderOutputIsCorrectAndReferenceCountingIsBalanced() {
        for (BufferPreference preference : BufferPreference.values()) {
            for (boolean direct : new boolean[]{false, true}) {
                for (int length : new int[]{0, 1, 17, 1024, 65_536}) {
                    byte[] plain = sample(length);
                    byte[] cipherText = plain.clone();
                    for (int i = 0; i < cipherText.length; i++) {
                        cipherText[i] ^= KEY;
                    }
                    String label = preference + " direct=" + direct + " length=" + length;

                    TestCipher cipher = new TestCipher(preference, KEY);
                    EmbeddedChannel channel = new EmbeddedChannel(new MinecraftCipherDecoder(cipher));
                    ByteBuf in = buffer(cipherText, direct);

                    channel.writeInbound(in);
                    ByteBuf out = channel.readInbound();

                    assertEquals(1, cipher.processCalls, label);
                    assertTrue(out.refCnt() >= 1, "output must still be alive: " + label);
                    assertArrayEquals(plain, drain(out.duplicate()), label);
                    assertTrue(out.release(), "downstream release must free everything: " + label);
                    assertEquals(0, in.refCnt(), "input buffer leaked: " + label);
                    assertFalse(channel.finish(), label);
                }
            }
        }
    }

    @Test
    void decoderLeavesCallerVisibleReaderIndexAlone() {
        TestCipher cipher = new TestCipher(BufferPreference.DIRECT_PREFERRED, KEY);
        EmbeddedChannel channel = new EmbeddedChannel(new MinecraftCipherDecoder(cipher));
        byte[] cipherText = sample(100);
        ByteBuf in = buffer(cipherText, true);
        in.readerIndex(10); // bytes before the reader index must be ignored

        channel.writeInbound(in);
        ByteBuf out = channel.readInbound();
        assertEquals(90, out.readableBytes());
        out.release();
        assertEquals(0, in.refCnt());
    }

    @Test
    void decoderReleasesEverythingWhenTheCipherFails() {
        for (BufferPreference preference : BufferPreference.values()) {
            for (boolean direct : new boolean[]{false, true}) {
                TestCipher cipher = new TestCipher(preference, KEY, true);
                EmbeddedChannel channel = new EmbeddedChannel(new MinecraftCipherDecoder(cipher));
                ByteBuf in = buffer(sample(64), direct);

                assertThrows(DecoderException.class, () -> channel.writeInbound(in));
                assertEquals(0, in.refCnt(), "leak after failure: " + preference + " direct=" + direct);
            }
        }
    }

    @Test
    void encoderEncryptsAndReleasesInput() {
        for (BufferPreference preference : BufferPreference.values()) {
            for (boolean direct : new boolean[]{false, true}) {
                byte[] plain = sample(500);
                TestCipher cipher = new TestCipher(preference, KEY);
                EmbeddedChannel channel = new EmbeddedChannel(new MinecraftCipherEncoder(cipher));
                ByteBuf in = buffer(plain, direct);

                channel.writeOutbound(in);
                ByteBuf out = channel.readOutbound();

                byte[] expected = plain.clone();
                for (int i = 0; i < expected.length; i++) {
                    expected[i] ^= KEY;
                }
                assertArrayEquals(expected, drain(out.duplicate()), preference + " direct=" + direct);
                assertTrue(out.release());
                assertEquals(0, in.refCnt(), "encoder input leaked: " + preference + " direct=" + direct);
            }
        }
    }

    @Test
    void encoderReleasesEverythingWhenTheCipherFails() {
        for (BufferPreference preference : BufferPreference.values()) {
            TestCipher cipher = new TestCipher(preference, KEY, true);
            EmbeddedChannel channel = new EmbeddedChannel(new MinecraftCipherEncoder(cipher));
            ByteBuf in = buffer(sample(64), true);

            assertThrows(EncoderException.class, () -> channel.writeOutbound(in));
            assertEquals(0, in.refCnt(), "leak after failure: " + preference);
        }
    }

    @Test
    void encryptThenDecryptRestoresTheOriginalBytes() {
        byte[] plain = sample(4000);
        EmbeddedChannel encoder = new EmbeddedChannel(
                new MinecraftCipherEncoder(new TestCipher(BufferPreference.DIRECT_REQUIRED, KEY)));
        EmbeddedChannel decoder = new EmbeddedChannel(
                new MinecraftCipherDecoder(new TestCipher(BufferPreference.HEAP_PREFERRED, KEY)));

        encoder.writeOutbound(Unpooled.wrappedBuffer(plain));
        ByteBuf encrypted = encoder.readOutbound();
        assertNotEquals(plain[0], encrypted.getByte(encrypted.readerIndex()));

        decoder.writeInbound(encrypted);
        ByteBuf decrypted = decoder.readInbound();
        assertArrayEquals(plain, drain(decrypted.duplicate()));
        decrypted.release();
    }

    @Test
    void handlersCloseTheirCipherWhenRemoved() {
        TestCipher decryptor = new TestCipher(BufferPreference.HEAP_PREFERRED, KEY);
        TestCipher encryptor = new TestCipher(BufferPreference.HEAP_PREFERRED, KEY);
        new EmbeddedChannel(new MinecraftCipherDecoder(decryptor)).finish();
        new EmbeddedChannel(new MinecraftCipherEncoder(encryptor)).finish();
        assertEquals(1, decryptor.closeCalls);
        assertEquals(1, encryptor.closeCalls);
    }
}
