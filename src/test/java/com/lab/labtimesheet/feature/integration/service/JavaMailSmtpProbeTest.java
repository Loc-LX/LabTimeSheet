package com.lab.labtimesheet.feature.integration.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpConnection;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class JavaMailSmtpProbeTest {
    @Test
    void appliesFiniteTimeoutsAndConfiguredFromName() throws Exception {
        var sender = new CapturingMailSender();
        var probe = new JavaMailSmtpProbe(() -> sender);
        var connection = new SmtpConnection(
                "smtp.example.com", 587, SecurityMode.STARTTLS, "user", "password",
                "noreply@example.com", "Lab Timesheet");

        probe.send(connection, "admin@example.com", "Subject", "Body");

        assertThat(sender.getJavaMailProperties())
                .containsEntry("mail.smtp.connectiontimeout", "5000")
                .containsEntry("mail.smtp.timeout", "5000")
                .containsEntry("mail.smtp.writetimeout", "5000");
        var from = (InternetAddress) sender.message.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("noreply@example.com");
        assertThat(from.getPersonal()).isEqualTo("Lab Timesheet");
    }

    @Test
    void appliesFiniteTimeoutsToImplicitTlsTransport() {
        var sender = new CapturingMailSender();
        var probe = new JavaMailSmtpProbe(() -> sender);
        var connection = new SmtpConnection(
                "smtp.example.com", 465, SecurityMode.TLS, null, null,
                "noreply@example.com", "Lab Timesheet");

        probe.send(connection, "admin@example.com", "Subject", "Body");

        assertThat(sender.getProtocol()).isEqualTo("smtps");
        assertThat(sender.getJavaMailProperties())
                .containsEntry("mail.smtps.connectiontimeout", "5000")
                .containsEntry("mail.smtps.timeout", "5000")
                .containsEntry("mail.smtps.writetimeout", "5000");
    }

    static final class CapturingMailSender extends JavaMailSenderImpl {
        private MimeMessage message;

        @Override
        public void send(MimeMessage... mimeMessages) {
            assertThat(mimeMessages).hasSize(1);
            message = mimeMessages[0];
        }
    }
}
