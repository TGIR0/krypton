package me.steinborn.krypton.mod.shared.network.util;

public final class VarIntUtil {

    private VarIntUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static int getVarIntLength(int value) {
        return (352 - (Integer.numberOfLeadingZeros(value) * 9)) >>> 6;
    }
}