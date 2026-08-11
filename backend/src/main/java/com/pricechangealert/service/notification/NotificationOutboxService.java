package com.pricechangealert.service.notification;

import com.pricechangealert.model.Market;
import com.pricechangealert.model.NotificationDelivery;
import com.pricechangealert.model.NotificationPreference;
import com.pricechangealert.repository.NotificationDeliveryRepository;
import com.pricechangealert.repository.NotificationPreferenceRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** Creates one independently retryable outbox row per enabled channel. */
@Service
public class NotificationOutboxService {

    private final NotificationPreferenceRepository preferences;
    private final NotificationDeliveryRepository deliveries;

    public NotificationOutboxService(NotificationPreferenceRepository preferences,
                                     NotificationDeliveryRepository deliveries) {
        this.preferences = preferences;
        this.deliveries = deliveries;
    }

    public void enqueue(String ownerId, String symbol, Market market, String message) {
        List<NotificationPreference> enabled = preferences.findAllByUserIdAndEnabledTrue(ownerId);
        for (NotificationPreference preference : enabled) {
            if (preference.getDestination() == null || preference.getDestination().isBlank()) continue;
            NotificationDelivery delivery = new NotificationDelivery();
            delivery.setOwnerId(ownerId);
            delivery.setChannel(preference.getChannel());
            delivery.setDestination(preference.getDestination());
            delivery.setSymbol(symbol);
            delivery.setMarket(market.name());
            delivery.setMessage(message);
            delivery.setNextAttemptAt(Instant.now());
            deliveries.save(delivery);
        }
    }
}
