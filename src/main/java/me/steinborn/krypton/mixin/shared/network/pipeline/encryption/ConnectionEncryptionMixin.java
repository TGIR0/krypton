package me.steinborn.krypton.mixin.shared.network.pipeline.encryption;

import com.velocitypowered.natives.encryption.VelocityCipher;
import com.velocitypowered.natives.util.Natives;
import io.netty.channel.Channel;
import me.steinborn.krypton.mod.shared.misc.KryptonPipelineEvent;
import me.steinborn.krypton.mod.shared.network.ClientConnectionEncryptionExtension;
import me.steinborn.krypton.mod.shared.network.pipeline.MinecraftCipherDecoder;
import me.steinborn.krypton.mod.shared.network.pipeline.MinecraftCipherEncoder;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;

@Mixin(Connection.class)
public class ConnectionEncryptionMixin implements ClientConnectionEncryptionExtension {
    @Shadow
    private Channel channel;

    @Shadow
    private boolean encrypted;

    @Unique
    private boolean krypton$encryptionEnabled = false;

    @Unique
    private SecretKey krypton$pendingSecretKey;

    @Override
    public void prepareEncryption(SecretKey key) {
        this.krypton$pendingSecretKey = key;
    }

    @Override
    public void setupEncryption(SecretKey key) throws GeneralSecurityException {
        if (this.krypton$encryptionEnabled) {
            return;
        }

        VelocityCipher decryption = Natives.cipher.get().forDecryption(key);
        VelocityCipher encryption = Natives.cipher.get().forEncryption(key);

        this.channel.pipeline().addBefore("splitter", "decrypt", new MinecraftCipherDecoder(decryption));
        this.channel.pipeline().addBefore("prepender", "encrypt", new MinecraftCipherEncoder(encryption));
        this.channel.pipeline().fireUserEventTriggered(KryptonPipelineEvent.ENCRYPTION_ENABLED);

        this.krypton$encryptionEnabled = true;
        this.encrypted = true;
    }

    @Inject(method = "setEncryptionKey", at = @At("HEAD"), cancellable = true)
    private void krypton$onSetEncryptionKey(Cipher decryptCipher, Cipher encryptCipher, CallbackInfo ci) {
        SecretKey key = this.krypton$pendingSecretKey;
        if (key != null) {
            this.krypton$pendingSecretKey = null;
            try {
                this.setupEncryption(key);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Unable to initialize Krypton native cipher", e);
            }
            ci.cancel();
        }
    }
}
