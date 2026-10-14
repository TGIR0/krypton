package me.steinborn.krypton.mixin.client.network.pipeline.encryption;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.steinborn.krypton.mod.shared.network.ClientConnectionEncryptionExtension;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.Key;

@Environment(EnvType.CLIENT)
@Mixin(ClientHandshakePacketListenerImpl.class)
public class ClientHandshakePacketListenerImplMixin {
    @Shadow
    @Final
    private Connection connection;

    @WrapOperation(
            method = "handleHello",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Crypt;getCipher(ILjava/security/Key;)Ljavax/crypto/Cipher;"
            )
    )
    private Cipher krypton$prepareVelocityCipher(int opMode, Key secretKey, Operation<Cipher> original) {
        // Defer pipeline injection until Connection#setEncryptionKey runs in the PacketSendListener
        // callback after the unencrypted ServerboundKeyPacket is sent.
        ((ClientConnectionEncryptionExtension) this.connection).prepareEncryption((SecretKey) secretKey);
        return null;
    }
}
