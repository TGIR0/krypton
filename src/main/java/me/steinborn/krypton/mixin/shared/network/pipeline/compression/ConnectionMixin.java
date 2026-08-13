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
        if (compressionThreshold < 0) {
            if (isKryptonOrVanillaDecompressor(this.channel.pipeline().get("decompress"))) {
                this.channel.pipeline().remove("decompress");
            }
            if (isKryptonOrVanillaCompressor(this.channel.pipeline().get("compress"))) {
                this.channel.pipeline().remove("compress");
            }
            this.channel.pipeline().fireUserEventTriggered(KryptonPipelineEvent.COMPRESSION_DISABLED);
        } else {
            ChannelHandler existingDecoder = channel.pipeline().get("decompress");
            ChannelHandler existingEncoder = channel.pipeline().get("compress");

            if (existingDecoder instanceof MinecraftCompressDecoder
                    && existingEncoder instanceof MinecraftCompressEncoder) {
                ((MinecraftCompressDecoder) existingDecoder).setThreshold(compressionThreshold);
                ((MinecraftCompressEncoder) existingEncoder).setThreshold(compressionThreshold);
                this.channel.pipeline().fireUserEventTriggered(KryptonPipelineEvent.COMPRESSION_THRESHOLD_UPDATED);
            } else {
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

                this.channel.pipeline().fireUserEventTriggered(KryptonPipelineEvent.COMPRESSION_ENABLED);
            }
        }
        ci.cancel();
    }

    private static boolean isKryptonOrVanillaDecompressor(Object o) {
        return o instanceof CompressionDecoder || o instanceof MinecraftCompressDecoder;
    }

    private static boolean isKryptonOrVanillaCompressor(Object o) {
        return o instanceof CompressionEncoder || o instanceof MinecraftCompressEncoder;
    }
}