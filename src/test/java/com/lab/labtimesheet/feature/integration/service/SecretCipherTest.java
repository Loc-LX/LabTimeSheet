package com.lab.labtimesheet.feature.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.config.SecurityProperties;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

    private static final String MASTER_KEY = Base64.getEncoder().encodeToString(new byte[] {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
        16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31
    });

    @Test
    void encryptsWithAesGcmRoundTripFreshNinetySixBitNoncesAndKeyVersion() {
        var cipher = new SecretCipher(properties(MASTER_KEY));

        var first = cipher.encrypt("holiday-api-secret");
        var second = cipher.encrypt("holiday-api-secret");

        assertThat(cipher.decrypt(first.ciphertext(), first.nonce())).isEqualTo("holiday-api-secret");
        assertThat(first.nonce()).hasSize(12);
        assertThat(first.keyVersion()).isEqualTo(1);
        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(first.ciphertext()).doesNotContain((byte) 'h');
    }

    @Test
    void wrongMasterKeyCannotDecryptTheStoredSecret() {
        var encrypted = new SecretCipher(properties(MASTER_KEY)).encrypt("holiday-api-secret");
        String otherKey = Base64.getEncoder().encodeToString(new byte[] {
            31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16,
            15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0
        });

        assertThatThrownBy(() -> new SecretCipher(properties(otherKey))
                .decrypt(encrypted.ciphertext(), encrypted.nonce()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to decrypt integration secret");
    }

    private static SecurityProperties properties(String masterKey) {
        var properties = new SecurityProperties();
        properties.setMasterKey(masterKey);
        return properties;
    }
}
