package com.pricechangealert.service.notification;

import com.pricechangealert.model.NotificationChannel;
import com.pricechangealert.model.NotificationDelivery;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class TelegramNotificationSender implements NotificationSender {

    private final WebClient webClient;
    private final String botToken;

    public TelegramNotificationSender(@Value("${price-change-alert.notifications.telegram.bot-token:}")
                                      String botToken) {
        this.webClient = WebClient.builder().build();
        this.botToken = botToken;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.TELEGRAM;
    }

    @Override
    public boolean available() {
        return !botToken.isBlank();
    }

    @Override
    public String availabilityMessage() {
        return available() ? "Enter the chat ID after starting the configured bot"
                : "Telegram notifications are not available right now. Please choose another channel.";
    }

    @Override
    public DeliveryResult send(NotificationDelivery delivery) {
        if (!available()) return DeliveryResult.failed(availabilityMessage());
        try {
            webClient.post()
                    .uri("https://api.telegram.org/bot{token}/sendMessage", botToken)
                    .bodyValue(Map.of(
                            "chat_id", delivery.getDestination(),
                            "text", "Price alert: " + delivery.getSymbol() + "\n" + delivery.getMessage()))
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(10))
                    .block();
            return DeliveryResult.sent();
        } catch (WebClientResponseException exception) {
            HttpStatusCode status = exception.getStatusCode();
            return status.is5xxServerError()
                    ? DeliveryResult.retry("Telegram returned " + status.value())
                    : DeliveryResult.failed("Telegram rejected the destination (" + status.value() + ")");
        } catch (RuntimeException exception) {
            return DeliveryResult.retry("Telegram request failed");
        }
    }
}
