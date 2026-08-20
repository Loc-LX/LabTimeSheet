package com.lab.labtimesheet.feature.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import com.lab.labtimesheet.config.SecurityProperties;
import org.junit.jupiter.api.Test;

class SecretCipherTest {
    @Test
    void roundTripsWithFreshNonceAndVersionedEnvelope() {
        SecretCipher cipher = new SecretCipher(properties(0));

        var encrypted = cipher.encrypt("holiday-api-key");

        assertThat(encrypted.nonce()).hasSize(12);
        assertThat(encrypted.keyVersion()).isEqualTo(1);
        assertThat(cipher.decrypt(encrypted.ciphertext(), encrypted.nonce())).isEqualTo("holiday-api-key");
    }

    @Test
    void wrongMasterKeyFailsWithoutReturningTheSecret() {
        SecretCipher writer = new SecretCipher(properties(0));
        var encrypted = writer.encrypt("holiday-api-key");
        SecretCipher reader = new SecretCipher(properties(1));

        assertThatThrownBy(() -> reader.decrypt(encrypted.ciphertext(), encrypted.nonce()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to decrypt integration secret")
                .hasMessageNotContaining("holiday-api-key");
    }

    private static SecurityProperties properties(int offset) {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) (index + offset + 1);
        }
        SecurityProperties properties = new SecurityProperties();
        properties.setMasterKey(Base64.getEncoder().encodeToString(key));
        return properties;
    }
}
