package me.steinborn.krypton.mod.shared.network.compression;

import com.velocitypowered.natives.compression.VelocityCompressor;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

import static com.google.common.base.Preconditions.checkState;
import static com.velocitypowered.natives.util.MoreByteBufUtils.ensureCompatible;
import static com.velocitypowered.natives.util.MoreByteBufUtils.preferredBuffer;

/**
 * Decompresses a Minecraft packet.
 * <p>
 * Every packet starts with a VarInt holding the <em>claimed</em> uncompressed size, where 0 means
 * "this packet was not compressed". That number comes from the other side of the connection, so it
 * is untrusted: it is checked before it is used to allocate a buffer.
 */
public class MinecraftCompressDecoder extends MessageToMessageDecoder<ByteBuf> {

    private static final int VANILLA_MAXIMUM_UNCOMPRESSED_SIZE = 8 * 1024 * 1024; // 8 MiB
    private static final int HARD_MAXIMUM_UNCOMPRESSED_SIZE = 128 * 1024 * 1024; // 128 MiB

    /** Largest uncompressed packet we accept (8 MiB, or 128 MiB with -Dkrypton.permit-oversized-packets=true). */
    private static final int UNCOMPRESSED_CAP =
            Boolean.getBoolean("krypton.permit-oversized-packets")
                    ? HARD_MAXIMUM_UNCOMPRESSED_SIZE : VANILLA_MAXIMUM_UNCOMPRESSED_SIZE;

    private int threshold;
    private final VelocityCompressor compressor;
    private final boolean validate;

    public MinecraftCompressDecoder(int threshold, boolean validate, VelocityCompressor compressor) {
        this.threshold = threshold;
        this.compressor = compressor;
        this.validate = validate;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        FriendlyByteBuf bb = new FriendlyByteBuf(in);
        int claimedUncompressedSize = bb.readVarInt();

        if (claimedUncompressedSize == 0) {
            int actualUncompressedSize = in.readableBytes();
            checkState(actualUncompressedSize < threshold, "Actual uncompressed size %s is greater than"
                    + " threshold %s", actualUncompressedSize, threshold);
            out.add(in.retain());
            return;
        }

        // These two size checks run even when `validate` is false. `validate` only controls the
        // protocol rule about the threshold; without these checks a malicious peer could claim a
        // huge (or negative) size and make us allocate a giant buffer for a tiny packet.
        checkState(claimedUncompressedSize >= 0,
                "Uncompressed size %s must not be negative", claimedUncompressedSize);
        checkState(claimedUncompressedSize <= UNCOMPRESSED_CAP,
                "Uncompressed size %s exceeds hard threshold of %s", claimedUncompressedSize,
                UNCOMPRESSED_CAP);

        if (validate) {
            checkState(claimedUncompressedSize >= threshold, "Uncompressed size %s is less than"
                    + " threshold %s", claimedUncompressedSize, threshold);
        }

        ByteBuf compatibleIn = ensureCompatible(ctx.alloc(), compressor, in);
        ByteBuf uncompressed = preferredBuffer(ctx.alloc(), compressor, claimedUncompressedSize);
        try {
            compressor.inflate(compatibleIn, uncompressed, claimedUncompressedSize);
            out.add(uncompressed);
        } catch (Exception e) {
            uncompressed.release();
            throw e;
        } finally {
            compatibleIn.release();
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        compressor.close();
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }
}
