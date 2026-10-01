package me.steinborn.krypton.mixin.shared.network.pipeline;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.server.network.LegacyQueryHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixes into {@link LegacyQueryHandler} to avoid processing messages after the
 * channel has already become inactive and to release reference-counted data
 * correctly when cancelling the read.
 */
@Mixin(LegacyQueryHandler.class)
public abstract class LegacyQueryHandlerMixin {
    @Inject(method = "channelRead", at = @At(value = "HEAD"), cancellable = true)
    public void channelRead(ChannelHandlerContext ctx, Object msg, CallbackInfo ci) {
        if (!ctx.channel().isActive()) {
            ReferenceCountUtil.release(msg);
            ci.cancel();
        }
    }
}
