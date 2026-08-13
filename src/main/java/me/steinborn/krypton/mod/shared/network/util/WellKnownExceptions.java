package me.steinborn.krypton.mod.shared.network.util;

public final class WellKnownExceptions {

    public static final QuietDecoderException BAD_LENGTH_CACHED = new QuietDecoderException("Bad packet length");
    public static final QuietDecoderException VARINT_BIG_CACHED = new QuietDecoderException("VarInt too big");

    private WellKnownExceptions() {
        throw new UnsupportedOperationException("Utility class");
    }
}
