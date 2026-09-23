package com.lab.labtimesheet.platform.service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.Supplier;

import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpConnection;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends immediate SMTP messages through a freshly configured JavaMail client.
 * Connections are bounded by finite network timeouts so an Admin test or
 * activation delivery cannot block a request indefinitely.
 */
@Component
class JavaMailSmtpProbe implements SmtpProbe {
    private static final String TIMEOUT_MILLIS = "5000";

    private final Supplier<JavaMailSenderImpl> senderFactory;

    JavaMailSmtpProbe() {
        this(JavaMailSenderImpl::new);
    }

    JavaMailSmtpProbe(Supplier<JavaMailSenderImpl> senderFactory) {
        this.senderFactory = senderFactory;
    }

    @Override
    public void send(SmtpConnection connection, String recipient, String subject, String body) {
        JavaMailSenderImpl sender = senderFactory.get();
        sender.setHost(connection.host());
        sender.setPort(connection.port());
        sender.setUsername(connection.username());
        sender.setPassword(connection.password());
        Properties properties = sender.getJavaMailProperties();
        if (connection.securityMode() == SecurityMode.STARTTLS) {
            properties.setProperty("mail.smtp.starttls.enable", "true");
            properties.setProperty("mail.smtp.starttls.required", "true");
        } else if (connection.securityMode() == SecurityMode.TLS) {
            sender.setProtocol("smtps");
        }
        String propertyPrefix = connection.securityMode() == SecurityMode.TLS ? "mail.smtps" : "mail.smtp";
        properties.setProperty(propertyPrefix + ".connectiontimeout", TIMEOUT_MILLIS);
        properties.setProperty(propertyPrefix + ".timeout", TIMEOUT_MILLIS);
        properties.setProperty(propertyPrefix + ".writetimeout", TIMEOUT_MILLIS);

        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(connection.fromAddress(), connection.fromName());
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body);
        } catch (MessagingException | UnsupportedEncodingException exception) {
            throw new IllegalStateException("Unable to construct SMTP message", exception);
        }
        sender.send(message);
    }
}
