package com.pricechangealert.service.notification;

import com.pricechangealert.model.NotificationChannel;
import com.pricechangealert.model.NotificationDelivery;
import com.pricechangealert.repository.NotificationDeliveryRepository;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Claims delivery rows in short transactions, performs network I/O without a database lock,
 * and records an at-least-once delivery result with bounded exponential retry.
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final NotificationDeliveryRepository repository;
    private final NotificationDeliveryStateService stateService;
    private final Map<NotificationChannel, NotificationSender> senders;
    private final int maxAttempts;

    public NotificationDispatchService(NotificationDeliveryRepository repository,
                                       NotificationDeliveryStateService stateService,
                                       List<NotificationSender> notificationSenders,
                                       @Value("${price-change-alert.notifications.dispatch.max-attempts:5}")
                                       int maxAttempts) {
        this.repository = repository;
        this.stateService = stateService;
        this.senders = new EnumMap<>(NotificationChannel.class);
        notificationSenders.forEach(sender -> this.senders.put(sender.channel(), sender));
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Scheduled(fixedDelayString = "${price-change-alert.notifications.dispatch.interval-ms:10000}")
    public void dispatchDue() {
        List<Long> ids = repository.findDispatchableIds(Instant.now(),
                NotificationDelivery.Status.PENDING, NotificationDelivery.Status.PROCESSING,
                PageRequest.of(0, 100));
        for (Long id : ids) {
            try {
                stateService.claim(id).ifPresent(this::sendAndComplete);
            } catch (RuntimeException exception) {
                log.warn("Notification delivery {} failed unexpectedly", id, exception);
            }
        }
    }

    private void sendAndComplete(NotificationDelivery delivery) {
        NotificationSender sender = senders.get(delivery.getChannel());
        DeliveryResult result;
        if (sender == null || !sender.available()) {
            result = DeliveryResult.failed(sender == null
                    ? "No sender is installed for " + delivery.getChannel()
                    : sender.availabilityMessage());
        } else {
            try {
                result = sender.send(delivery);
            } catch (RuntimeException exception) {
                result = DeliveryResult.retry(exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage());
            }
        }
        stateService.complete(delivery.getId(), result, maxAttempts);
    }
}
