package com.pricechangealert.service.notification;

import com.pricechangealert.model.NotificationChannel;
import com.pricechangealert.model.NotificationDelivery;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationSender implements NotificationSender {

    private final ObjectProvider<JavaMailSender> mailSenders;
    private final boolean enabled;
    private final String from;
    private final String baseUrl;
    private final String smtpHost;

    public EmailNotificationSender(ObjectProvider<JavaMailSender> mailSenders,
                                   @Value("${price-change-alert.notifications.email.enabled:false}")
                                   boolean enabled,
                                   @Value("${price-change-alert.notifications.email.from:}") String from,
                                   @Value("${price-change-alert.base-url:http://localhost:8080}") String baseUrl,
                                   @Value("${spring.mail.host:}") String smtpHost) {
        this.mailSenders = mailSenders;
        this.enabled = enabled;
        this.from = from;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.smtpHost = smtpHost;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public boolean available() {
        return enabled && !from.isBlank() && !smtpHost.isBlank()
                && mailSenders.getIfAvailable() != null;
    }

    @Override
    public String availabilityMessage() {
        return available() ? "Uses your account email"
                : "Email notifications are not available right now. Please choose another channel.";
    }

    @Override
    public DeliveryResult send(NotificationDelivery delivery) {
        JavaMailSender mailSender = mailSenders.getIfAvailable();
        if (!available() || mailSender == null) return DeliveryResult.failed(availabilityMessage());
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(delivery.getDestination());
        mail.setSubject("Price alert: " + delivery.getSymbol());
        mail.setText(delivery.getMessage() + "\n\nOpen your alerts: " + baseUrl + "/#alerts");
        try {
            mailSender.send(mail);
            return DeliveryResult.sent();
        } catch (MailException exception) {
            return DeliveryResult.retry(exception.getMessage());
        }
    }
}
