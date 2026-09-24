package com.lab.labtimesheet.platform;

import java.util.Base64;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Security material used to encrypt integration credentials at rest. */
@ConfigurationProperties("lab.security")
@Getter
@Setter
public class SecurityProperties {
    private String masterKey;
    private String trustedProxyCidrs;

    /**
     * Returns the explicitly configured reverse-proxy socket networks used only by the production forwarded-header
     * filter. An absent value is acceptable in dev/test but fails production readiness.
     *
     * @return comma-separated numeric IPv4/IPv6 CIDRs, or {@code null} when not configured
     */
    public String getTrustedProxyCidrs() {
        return trustedProxyCidrs;
    }

    /**
     * Sets the explicitly trusted reverse-proxy socket networks.
     *
     * @param trustedProxyCidrs comma-separated numeric IPv4/IPv6 CIDRs
     */
    public void setTrustedProxyCidrs(String trustedProxyCidrs) {
        this.trustedProxyCidrs = trustedProxyCidrs;
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
