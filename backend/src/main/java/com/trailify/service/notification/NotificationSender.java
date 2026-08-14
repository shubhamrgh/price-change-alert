package com.trailify.service.notification;

import com.trailify.model.NotificationChannel;
import com.trailify.model.NotificationDelivery;

public interface NotificationSender {
    NotificationChannel channel();
    boolean available();
    String availabilityMessage();
    DeliveryResult send(NotificationDelivery delivery);
}
