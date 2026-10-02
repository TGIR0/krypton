package me.steinborn.krypton.mod.shared.network.testsupport;

import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.natives.util.BufferPreference;
import io.netty.buffer.ByteBuf;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A {@link VelocityCompressor} backed by the JDK's zlib so tests do not need native libraries.
 * It counts how it is used so tests can assert that rejected packets never reach the compressor.
 */
public final class TestCompressor implements VelocityCompressor {
    private final BufferPreference preference;
    public int inflateCalls;
    public int deflateCalls;
    public int closeCalls;

    public TestCompressor() {
        this(BufferPreference.HEAP_PREFERRED);
    }

    public TestCompressor(BufferPreference preference) {
        this.preference = preference;
    }

    @Override
    public void inflate(ByteBuf source, ByteBuf destination, int uncompressedSize) throws DataFormatException {
        inflateCalls++;
        byte[] input = new byte[source.readableBytes()];
        source.readBytes(input);

        Inflater inflater = new Inflater();
        try {
            inflater.setInput(input);
            byte[] output = new byte[uncompressedSize];
            int total = 0;
            while (total < uncompressedSize && !inflater.finished()) {
                int n = inflater.inflate(output, total, uncompressedSize - total);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                total += n;
            }
            if (total != uncompressedSize || !inflater.finished()) {
                throw new DataFormatException("Inflated " + total + " bytes, expected " + uncompressedSize);
            }
            destination.writeBytes(output, 0, total);
        } finally {
            inflater.end();
        }
    }

    @Override
    public void deflate(ByteBuf source, ByteBuf destination) {
        deflateCalls++;
        byte[] input = new byte[source.readableBytes()];
        source.readBytes(input);

        Deflater deflater = new Deflater(4);
        try {
            deflater.setInput(input);
            deflater.finish();
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            while (!deflater.finished()) {
                int n = deflater.deflate(chunk);
                result.write(chunk, 0, n);
            }
            destination.writeBytes(result.toByteArray());
        } finally {
            deflater.end();
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
