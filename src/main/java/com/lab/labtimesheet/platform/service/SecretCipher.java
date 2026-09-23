package com.lab.labtimesheet.platform.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import com.lab.labtimesheet.platform.SecurityProperties;
import com.lab.labtimesheet.platform.model.dto.EncryptedSecret;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Encrypts integration credentials, including SMTP and HolidayAPI secrets, with AES-256-GCM using a fresh nonce per stored revision. */
@Component
public class SecretCipher {
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    SecretCipher(SecurityProperties properties) {
        key = new SecretKeySpec(properties.decodedMasterKey(), "AES");
    }

    /**
     * Encrypts one plaintext secret with a fresh nonce.
     *
     * @param plaintext secret material to protect
     * @return ciphertext, nonce and key version ready for persistence
     * @throws IllegalStateException when the platform cipher cannot be initialised
     */
    public EncryptedSecret encrypt(String plaintext) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new EncryptedSecret(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)), nonce, 1);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt integration secret", exception);
        }
    }

    /**
     * Decrypts one stored secret revision.
     *
     * @param ciphertext stored ciphertext
     * @param nonce nonce stored beside the ciphertext
     * @return recovered plaintext
     * @throws IllegalStateException when the key does not match or the envelope is malformed
     */
    public String decrypt(byte[] ciphertext, byte[] nonce) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to decrypt integration secret", exception);
        }
    }
}
