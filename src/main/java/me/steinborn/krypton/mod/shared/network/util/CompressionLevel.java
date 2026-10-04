package me.steinborn.krypton.mod.shared.network.util;

/**
 * The zlib compression level used for network packets.
 * <p>
 * A higher level makes packets smaller but costs more CPU time per packet; a lower level is
 * faster but sends more bytes. The default (4) is a balanced choice. Server owners can change it
 * with {@code -Dkrypton.compression-level=<1-9>}. This only affects what this side sends; the other
 * side can always decompress any level.
 */
public final class CompressionLevel {
    public static final String PROPERTY = "krypton.compression-level";
    public static final int DEFAULT = 4;
    public static final int MIN = 1;
    public static final int MAX = 9;

    private static final int LEVEL = resolve(System.getProperty(PROPERTY));

    private CompressionLevel() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** The level to use for new connections. */
    public static int get() {
        return LEVEL;
    }

    /**
     * Parses a configured level. Missing, invalid, or out-of-range values fall back to
     * {@link #DEFAULT} instead of failing, so a typo in a startup flag cannot break networking.
     */
    public static int resolve(String raw) {
        if (raw == null) {
            return DEFAULT;
        }
        try {
            int level = Integer.parseInt(raw.trim());
            return (level >= MIN && level <= MAX) ? level : DEFAULT;
        } catch (NumberFormatException e) {
            return DEFAULT;
        }
    }
}
