package me.steinborn.krypton.mod.shared.network;

import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;

public interface ClientConnectionEncryptionExtension {
    void prepareEncryption(SecretKey key);

    void setupEncryption(SecretKey key) throws GeneralSecurityException;
}
