package me.steinborn.krypton.mixin.shared.network.pipeline.encryption;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.steinborn.krypton.mod.shared.network.ClientConnectionEncryptionExtension;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.security.Key;

@Mixin(ServerLoginPacketListenerImpl.class)
public class ServerLoginPacketListenerImplMixin {
    @Shadow
    @Final
    Connection connection;

    @WrapOperation(
            method = "handleKey",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Crypt;getCipher(ILjava/security/Key;)Ljavax/crypto/Cipher;"
            )
    )
    private Cipher krypton$initializeVelocityCipher(int opMode, Key secretKey, Operation<Cipher> original) throws GeneralSecurityException {
        // Initialize Krypton's native cipher on the connection and skip JCE Cipher allocation.
        ((ClientConnectionEncryptionExtension) this.connection).setupEncryption((SecretKey) secretKey);
        return null;
    }

    @WrapOperation(
            method = "handleKey",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/Connection;setEncryptionKey(Ljavax/crypto/Cipher;Ljavax/crypto/Cipher;)V"
            )
    )
    private void krypton$skipVanillaEncryptionPipelineInjection(Connection connection, Cipher decryptCipher, Cipher encryptCipher, Operation<Void> original) {
        if (decryptCipher != null && encryptCipher != null) {
            original.call(connection, decryptCipher, encryptCipher);
        }
    }
}
