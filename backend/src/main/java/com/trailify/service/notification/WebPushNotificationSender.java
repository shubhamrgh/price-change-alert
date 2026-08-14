package com.trailify.service.notification;

import com.trailify.model.Market;
import com.trailify.model.NotificationChannel;
import com.trailify.model.NotificationDelivery;
import com.trailify.service.PushService;
import org.springframework.stereotype.Component;

@Component
public class WebPushNotificationSender implements NotificationSender {

    private final PushService pushService;

    public WebPushNotificationSender(PushService pushService) {
        this.pushService = pushService;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WEB_PUSH;
    }

    @Override
    public boolean available() {
        return pushService.available();
    }

    @Override
    public String availabilityMessage() {
        return available() ? "Mobile and browser notifications"
                : "Mobile notifications are not available right now. Please choose another channel.";
    }

    @Override
    public DeliveryResult send(NotificationDelivery delivery) {
        return pushService.notifyAll(delivery.getOwnerId(), delivery.getSymbol(),
                Market.valueOf(delivery.getMarket()), delivery.getMessage());
    }
}
