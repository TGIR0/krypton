package me.steinborn.krypton.mixin.client.network.pipeline.encryption;

import me.steinborn.krypton.mod.shared.network.ClientConnectionEncryptionExtension;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.Key;

@Environment(EnvType.CLIENT)
@Mixin(ClientHandshakePacketListenerImpl.class)
public class ClientHandshakePacketListenerImplMixin {
    @Shadow
    @Final
    private Connection connection;

    @Redirect(
            method = "handleHello",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Crypt;getCipher(ILjava/security/Key;)Ljavax/crypto/Cipher;"
            )
    )
    private Cipher onHello$prepareVelocityCipher(int ignoredOpMode, Key secretKey) {
        // Store the secret key on the connection so native encryption is enabled inside
        // Connection#setEncryptionKey, which runs in the PacketSendListener callback AFTER
        // the unencrypted ServerboundKeyPacket has been written to the wire.
        ((ClientConnectionEncryptionExtension) this.connection).prepareEncryption((SecretKey) secretKey);
        return null;
    }
}
