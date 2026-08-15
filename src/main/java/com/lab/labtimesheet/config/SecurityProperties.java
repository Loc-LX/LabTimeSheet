package com.lab.labtimesheet.config;

import java.util.Base64;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Security material used to encrypt integration credentials at rest. */
@ConfigurationProperties("lab.security")
public class SecurityProperties {
    private String masterKey;

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    /**
     * Decodes and validates the configured AES-256 master key.
     *
     * @return a newly decoded 32-byte key
     * @throws IllegalStateException when the property is absent or does not decode to exactly 256 bits
     */
    public byte[] decodedMasterKey() {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("lab.security.master-key is required");
        }
        byte[] decoded = Base64.getDecoder().decode(masterKey);
        if (decoded.length != 32) {
            throw new IllegalStateException("lab.security.master-key must decode to 256 bits");
        }
        return decoded;
    }
}
