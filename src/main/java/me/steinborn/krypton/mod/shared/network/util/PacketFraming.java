package me.steinborn.krypton.mod.shared.network.util;

import io.netty.handler.codec.EncoderException;

/**
 * Limits of the Minecraft packet frame format.
 * <p>
 * Every packet on the wire is prefixed with its length as a VarInt, and the protocol only allows
 * that prefix to be 3 bytes long (21 bits). The receiving side rejects anything longer, so a frame
 * that is too big is a bug on the sending side. Failing there gives a clear error instead of a
 * confusing disconnect on the other end, and avoids sending megabytes that will be thrown away.
 */
public final class PacketFraming {
    /** The largest frame the 3-byte length prefix can describe: 2^21 - 1 bytes. */
    public static final int MAX_FRAME_LENGTH = (1 << 21) - 1;

    private PacketFraming() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * @throws EncoderException if {@code length} cannot be written in a 3-byte length prefix
     */
    public static void checkFrameLength(int length) {
        if (length < 0 || length > MAX_FRAME_LENGTH) {
            throw new EncoderException("Packet of " + length + " bytes does not fit in a frame (maximum "
                    + MAX_FRAME_LENGTH + ")");
        }
    }
}
