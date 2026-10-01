package me.steinborn.krypton.mixin.shared.network.pipeline.compression;

import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.natives.util.Natives;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import me.steinborn.krypton.mod.shared.misc.KryptonPipelineEvent;
import me.steinborn.krypton.mod.shared.network.compression.MinecraftCompressDecoder;
import me.steinborn.krypton.mod.shared.network.compression.MinecraftCompressEncoder;
import net.minecraft.network.CompressionDecoder;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
    @Shadow
    private Channel channel;

    @Inject(method = "setupCompression", at = @At("HEAD"), cancellable = true)
    public void setupCompression(int compressionThreshold, boolean validate, CallbackInfo ci) {
        ChannelHandler existingDecoder = channel.pipeline().get("decompress");
        ChannelHandler existingEncoder = channel.pipeline().get("compress");

        if (compressionThreshold < 0) {
            boolean changed = false;

            if (isKryptonOrVanillaDecompressor(existingDecoder)) {
                channel.pipeline().remove(existingDecoder);
                changed = true;
            }
            if (isKryptonOrVanillaCompressor(existingEncoder)) {
                channel.pipeline().remove(existingEncoder);
                changed = true;
            }

            if (changed) {
                channel.pipeline().fireUserEventTriggered(KryptonPipelineEvent.COMPRESSION_DISABLED);
            }

            // Preserve unknown third-party handlers. Their lifecycle belongs to
            // the mod that installed them, so Krypton must not remove them.
            ci.cancel();
            return;
        }

        if (existingDecoder instanceof MinecraftCompressDecoder
                && existingEncoder instanceof MinecraftCompressEncoder) {
            ((MinecraftCompressDecoder) existingDecoder).setThreshold(compressionThreshold);
            ((MinecraftCompressEncoder) existingEncoder).setThreshold(compressionThreshold);
            channel.pipeline().fireUserEventTriggered(KryptonPipelineEvent.COMPRESSION_THRESHOLD_UPDATED);
            ci.cancel();
            return;
        }

        // A foreign handler under the vanilla names means another mod owns the
        // compression pipeline. Do not remove or replace it from Krypton.
        if (isForeignCompressionHandler(existingDecoder)
                || isForeignCompressionHandler(existingEncoder)) {
            ci.cancel();
            return;
        }

        if (existingDecoder != null) {
            channel.pipeline().remove(existingDecoder);
        }
        if (existingEncoder != null) {
            channel.pipeline().remove(existingEncoder);
        }

        VelocityCompressor compressor = Natives.compress.get().create(4);
        MinecraftCompressEncoder encoder = new MinecraftCompressEncoder(compressionThreshold, compressor);
        MinecraftCompressDecoder decoder = new MinecraftCompressDecoder(compressionThreshold, validate, compressor);

        channel.pipeline().addBefore("decoder", "decompress", decoder);
        channel.pipeline().addBefore("encoder", "compress", encoder);
        channel.pipeline().fireUserEventTriggered(KryptonPipelineEvent.COMPRESSION_ENABLED);
        ci.cancel();
    }

    private static boolean isForeignCompressionHandler(Object handler) {
        return handler != null
                && !(handler instanceof CompressionDecoder)
                && !(handler instanceof CompressionEncoder)
                && !(handler instanceof MinecraftCompressDecoder)
                && !(handler instanceof MinecraftCompressEncoder);
    }

    private static boolean isKryptonOrVanillaDecompressor(Object handler) {
        return handler instanceof CompressionDecoder || handler instanceof MinecraftCompressDecoder;
    }

    private static boolean isKryptonOrVanillaCompressor(Object handler) {
        return handler instanceof CompressionEncoder || handler instanceof MinecraftCompressEncoder;
    }
}
