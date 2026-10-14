package me.steinborn.krypton.mixin.shared.network.pipeline.compression;

import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.natives.util.Natives;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import me.steinborn.krypton.mod.shared.misc.KryptonPipelineEvent;
import me.steinborn.krypton.mod.shared.network.compression.MinecraftCompressDecoder;
import me.steinborn.krypton.mod.shared.network.compression.MinecraftCompressEncoder;
import me.steinborn.krypton.mod.shared.network.util.CompressionLevel;
import me.steinborn.krypton.mod.shared.network.util.SocketTuning;
import net.minecraft.network.CompressionDecoder;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionCompressionMixin {
    @Shadow
    private Channel channel;

    @Unique
    @Inject(method = "setupCompression", at = @At("HEAD"), cancellable = true)
    private void krypton$setupCompression(int compressionThreshold, boolean validate, CallbackInfo ci) {
        SocketTuning.applyLowLatency(this.channel);
        if (compressionThreshold < 0) {
            if (krypton$isKryptonOrVanillaDecompressor(this.channel.pipeline().get("decompress"))) {
                this.channel.pipeline().remove("decompress");
            }
            if (krypton$isKryptonOrVanillaCompressor(this.channel.pipeline().get("compress"))) {
                this.channel.pipeline().remove("compress");
            }
            this.channel.pipeline().fireUserEventTriggered(KryptonPipelineEvent.COMPRESSION_DISABLED);
        } else {
            ChannelHandler existingDecoder = this.channel.pipeline().get("decompress");
            ChannelHandler existingEncoder = this.channel.pipeline().get("compress");
            if (existingDecoder instanceof MinecraftCompressDecoder kryptonDecoder
                    && existingEncoder instanceof MinecraftCompressEncoder kryptonEncoder) {
                kryptonDecoder.setThreshold(compressionThreshold);
                kryptonEncoder.setThreshold(compressionThreshold);
                this.channel.pipeline().fireUserEventTriggered(KryptonPipelineEvent.COMPRESSION_THRESHOLD_UPDATED);
            } else {
                if (existingDecoder != null) {
                    this.channel.pipeline().remove(existingDecoder);
                }
                if (existingEncoder != null) {
                    this.channel.pipeline().remove(existingEncoder);
                }
                int level = CompressionLevel.get();
                VelocityCompressor decoderCompressor = Natives.compress.get().create(level);
                VelocityCompressor encoderCompressor = Natives.compress.get().create(level);
                MinecraftCompressDecoder decoder = new MinecraftCompressDecoder(compressionThreshold, validate, decoderCompressor);
                MinecraftCompressEncoder encoder = new MinecraftCompressEncoder(compressionThreshold, encoderCompressor);

                this.channel.pipeline().addBefore("decoder", "decompress", decoder);
                this.channel.pipeline().addBefore("encoder", "compress", encoder);
                this.channel.pipeline().fireUserEventTriggered(KryptonPipelineEvent.COMPRESSION_ENABLED);
            }
        }
        ci.cancel();
    }

    @Unique
    private static boolean krypton$isKryptonOrVanillaDecompressor(Object handler) {
        return handler instanceof CompressionDecoder || handler instanceof MinecraftCompressDecoder;
    }

    @Unique
    private static boolean krypton$isKryptonOrVanillaCompressor(Object handler) {
        return handler instanceof CompressionEncoder || handler instanceof MinecraftCompressEncoder;
    }
}
