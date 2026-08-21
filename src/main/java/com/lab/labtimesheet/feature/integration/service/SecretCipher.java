package com.lab.labtimesheet.feature.integration.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import com.lab.labtimesheet.config.SecurityProperties;
import com.lab.labtimesheet.feature.integration.model.dto.EncryptedSecret;
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

    EncryptedSecret encrypt(String plaintext) {
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

    String decrypt(byte[] ciphertext, byte[] nonce) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to decrypt integration secret", exception);
        }
    }
}
