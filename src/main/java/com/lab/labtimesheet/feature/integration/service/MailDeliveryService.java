package com.lab.labtimesheet.feature.integration.service;

import com.lab.labtimesheet.feature.integration.model.dto.SmtpConnection;
import com.lab.labtimesheet.feature.integration.model.entity.SmtpConfiguration;
import com.lab.labtimesheet.feature.integration.model.SmtpStatus;
import com.lab.labtimesheet.feature.integration.repository.SmtpConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-feature email delivery boundary backed by the single active SMTP revision.
 * Stored credentials are decrypted only while constructing the immediate adapter call.
 */
@Service
public class MailDeliveryService {
    private final SmtpConfigurationRepository configurations;
    private final SecretCipher secrets;
    private final SmtpProbe probe;

    MailDeliveryService(SmtpConfigurationRepository configurations, SecretCipher secrets, SmtpProbe probe) {
        this.configurations = configurations;
        this.secrets = secrets;
        this.probe = probe;
    }

    /**
     * Reports whether workflows may emit required email.
     *
     * @return {@code true} when an active tested SMTP revision exists
     */
    @Transactional(readOnly = true)
    public boolean isAvailable() {
        return configurations.existsByStatus(SmtpStatus.ACTIVE);
    }

    /**
     * Sends one immediate message through the active configuration.
     *
     * @param recipient destination email address
     * @param subject message subject
     * @param body plain-text message body
     * @throws IllegalStateException when no active configuration exists or delivery fails
     */
    public void send(String recipient, String subject, String body) {
        probe.send(activeConnection(), recipient, subject, body);
    }

    /**
     * Resolves request-local connection material from the active encrypted configuration.
     *
     * @return complete connection values, including the transient decrypted password
     * @throws IllegalStateException when SMTP is not active
     */
    @Transactional(readOnly = true)
    public SmtpConnection activeConnection() {
        return configurations.findByStatus(SmtpStatus.ACTIVE)
                .map(this::connection)
                .orElseThrow(() -> new IllegalStateException("Active SMTP configuration is required"));
    }

    SmtpConnection connection(SmtpConfiguration configuration) {
        byte[] ciphertext = configuration.getPasswordCiphertext();
        return new SmtpConnection(
                configuration.getHost(), configuration.getPort(), configuration.getSecurityMode(),
                configuration.getUsername(),
                ciphertext == null ? null : secrets.decrypt(ciphertext, configuration.getPasswordNonce()),
                configuration.getFromAddress(), configuration.getFromName());
    }
}
