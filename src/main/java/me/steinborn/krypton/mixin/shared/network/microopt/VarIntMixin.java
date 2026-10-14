package me.steinborn.krypton.mixin.shared.network.microopt;

import io.netty.buffer.ByteBuf;
import me.steinborn.krypton.mod.shared.network.util.VarIntUtil;
import net.minecraft.network.VarInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(VarInt.class)
public class VarIntMixin {
    /**
     * @author Andrew Steinborn
     * @reason optimized version
     */
    @Overwrite
    public static int getByteSize(int v) {
        return VarIntUtil.getVarIntLength(v);
    }

    /**
     * @author Andrew Steinborn
     * @reason optimized version
     */
    @Overwrite
    public static ByteBuf write(ByteBuf buf, int value) {
        return VarIntUtil.writeVarInt(buf, value);
    }
}
