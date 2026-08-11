package com.pricechangealert.service.notification;

import com.pricechangealert.model.NotificationChannel;
import com.pricechangealert.model.NotificationDelivery;

public interface NotificationSender {
    NotificationChannel channel();
    boolean available();
    String availabilityMessage();
    DeliveryResult send(NotificationDelivery delivery);
}
