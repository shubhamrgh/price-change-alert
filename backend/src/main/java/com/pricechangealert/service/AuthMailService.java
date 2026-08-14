package com.pricechangealert.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class AuthMailService {

    private final ObjectProvider<JavaMailSender> mailSenders;
    private final boolean enabled;
    private final String from;
    private final String smtpHost;

    public AuthMailService(ObjectProvider<JavaMailSender> mailSenders,
                           @Value("${price-change-alert.auth.email.enabled:false}") boolean enabled,
                           @Value("${price-change-alert.auth.email.from:}") String from,
                           @Value("${spring.mail.host:}") String smtpHost) {
        this.mailSenders = mailSenders;
        this.enabled = enabled;
        this.from = from;
        this.smtpHost = smtpHost;
    }

    public boolean available() {
        return enabled && !from.isBlank() && !smtpHost.isBlank()
                && mailSenders.getIfAvailable() != null;
    }

    public void sendMagicLink(String email, String link) {
        send(email, "Your Tailify sign-in link",
                "Use this secure link to sign in:\n\n" + link
                        + "\n\nThis link expires in 15 minutes and can be used once."
                        + " If you did not request it, ignore this email.");
    }

    public void sendPasswordReset(String email, String link) {
        send(email, "Reset your Tailify password",
                "Use this secure link to choose a new password:\n\n" + link
                        + "\n\nThis link expires in 30 minutes and can be used once."
                        + " If you did not request it, ignore this email.");
    }

    private void send(String to, String subject, String text) {
        JavaMailSender sender = mailSenders.getIfAvailable();
        if (!available() || sender == null) {
            throw new IllegalStateException("Authentication email is not configured");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        sender.send(message);
    }
}
