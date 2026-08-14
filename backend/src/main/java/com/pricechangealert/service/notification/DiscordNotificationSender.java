package com.pricechangealert.service.notification;

import com.pricechangealert.model.NotificationChannel;
import com.pricechangealert.model.NotificationDelivery;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class DiscordNotificationSender implements NotificationSender {

    private final WebClient webClient;

    public DiscordNotificationSender() {
        this.webClient = WebClient.builder().build();
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.DISCORD;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String availabilityMessage() {
        return "Create an incoming webhook in your Discord channel";
    }

    @Override
    public DeliveryResult send(NotificationDelivery delivery) {
        try {
            webClient.post()
                    .uri(delivery.getDestination())
                    .bodyValue(Map.of(
                            "username", "Tailify",
                            "content", "**" + delivery.getSymbol() + "** · " + delivery.getMessage()))
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(10))
                    .block();
            return DeliveryResult.sent();
        } catch (WebClientResponseException exception) {
            HttpStatusCode status = exception.getStatusCode();
            return status.is5xxServerError() || status.value() == 429
                    ? DeliveryResult.retry("Discord returned " + status.value())
                    : DeliveryResult.failed("Discord rejected the webhook (" + status.value() + ")");
        } catch (RuntimeException exception) {
            return DeliveryResult.retry("Discord request failed");
        }
    }
}
