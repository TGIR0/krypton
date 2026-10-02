package me.steinborn.krypton.mod.shared.network.testsupport;

import com.velocitypowered.natives.encryption.VelocityCipher;
import com.velocitypowered.natives.util.BufferPreference;
import io.netty.buffer.ByteBuf;

/**
 * A trivial in-place "cipher" (XOR with a key byte). XOR is its own inverse, so one instance type
 * works for both directions. It can also be told to fail, to test error handling.
 */
public final class TestCipher implements VelocityCipher {
    private final BufferPreference preference;
    private final byte key;
    private final boolean failOnProcess;
    public int processCalls;
    public int closeCalls;

    public TestCipher(BufferPreference preference, int key) {
        this(preference, key, false);
    }

    public TestCipher(BufferPreference preference, int key, boolean failOnProcess) {
        this.preference = preference;
        this.key = (byte) key;
        this.failOnProcess = failOnProcess;
    }

    @Override
    public void process(ByteBuf source) {
        processCalls++;
        if (failOnProcess) {
            throw new IllegalStateException("simulated cipher failure");
        }
        for (int i = source.readerIndex(); i < source.writerIndex(); i++) {
            source.setByte(i, source.getByte(i) ^ key);
        }
    }

    @Override
    public BufferPreference preferredBufferType() {
        return preference;
    }

    @Override
    public void close() {
        closeCalls++;
    }
}
