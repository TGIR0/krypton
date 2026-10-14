package me.steinborn.krypton.mixin.shared.network.pipeline.prepender;

import io.netty.channel.ChannelOutboundHandler;
import me.steinborn.krypton.mod.shared.network.pipeline.MinecraftVarintPrepender;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Connection.class)
public class ConnectionPrependerMixin {
    @Unique
    @Inject(method = "createFrameEncoder", at = @At("HEAD"), cancellable = true)
    private static void krypton$useOptimizedVarintPrepender(boolean local, CallbackInfoReturnable<ChannelOutboundHandler> cir) {
        if (!local) {
            cir.setReturnValue(MinecraftVarintPrepender.INSTANCE);
        }
    }
}
