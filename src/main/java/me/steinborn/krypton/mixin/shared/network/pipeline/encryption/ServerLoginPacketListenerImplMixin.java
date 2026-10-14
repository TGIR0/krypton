package me.steinborn.krypton.mixin.shared.network.pipeline.encryption;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.steinborn.krypton.mod.shared.network.ClientConnectionEncryptionExtension;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

    @Unique
    @WrapOperation(
            method = "handleKey",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Crypt;getCipher(ILjava/security/Key;)Ljavax/crypto/Cipher;"
            )
    )
    private Cipher krypton$initializeVelocityCipher(int opMode, Key secretKey, Operation<Cipher> original) throws GeneralSecurityException {
        // Hijack this portion of the cipher initialization and set up our own encryption handler.
        ((ClientConnectionEncryptionExtension) this.connection).setupEncryption((SecretKey) secretKey);

        // Turn the operation into a no-op.
        return null;
    }

    @Unique
    @WrapOperation(
            method = "handleKey",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/Connection;setEncryptionKey(Ljavax/crypto/Cipher;Ljavax/crypto/Cipher;)V"
            )
    )
    private void krypton$ignoreMinecraftEncryptionPipelineInjection(Connection connection, Cipher decryptCipher, Cipher encryptCipher, Operation<Void> original) {
        if (decryptCipher != null && encryptCipher != null) {
            original.call(connection, decryptCipher, encryptCipher);
        }
    }
}
