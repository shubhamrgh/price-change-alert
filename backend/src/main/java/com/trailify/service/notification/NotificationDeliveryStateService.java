package com.trailify.service.notification;

import com.trailify.model.NotificationDelivery;
import com.trailify.repository.NotificationDeliveryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeliveryStateService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryStateService.class);
    private final NotificationDeliveryRepository repository;

    public NotificationDeliveryStateService(NotificationDeliveryRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<NotificationDelivery> claim(Long id) {
        NotificationDelivery delivery = repository.findByIdForUpdate(id).orElse(null);
        if (delivery == null || delivery.getNextAttemptAt().isAfter(Instant.now())
                || (delivery.getStatus() != NotificationDelivery.Status.PENDING
                    && delivery.getStatus() != NotificationDelivery.Status.PROCESSING)) {
            return Optional.empty();
        }
        delivery.setStatus(NotificationDelivery.Status.PROCESSING);
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        delivery.setNextAttemptAt(Instant.now().plus(Duration.ofMinutes(2)));
        repository.save(delivery);
        return Optional.of(delivery);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long id, DeliveryResult result, int maxAttempts) {
        NotificationDelivery delivery = repository.findByIdForUpdate(id).orElse(null);
        if (delivery == null || delivery.getStatus() != NotificationDelivery.Status.PROCESSING) return;
        if (result.delivered()) {
            delivery.setStatus(NotificationDelivery.Status.SENT);
            delivery.setSentAt(Instant.now());
            delivery.setLastError(null);
            log.info("Delivered {} notification {}", delivery.getChannel(), delivery.getId());
        } else {
            String error = truncate(result.error(), 1024);
            delivery.setLastError(error);
            if (result.retryable() && delivery.getAttemptCount() < maxAttempts) {
                delivery.setStatus(NotificationDelivery.Status.PENDING);
                long delaySeconds = Math.min(900, 30L << Math.min(10, delivery.getAttemptCount() - 1));
                delivery.setNextAttemptAt(Instant.now().plusSeconds(delaySeconds));
                log.warn("Will retry {} notification {}: {}", delivery.getChannel(), id, error);
            } else {
                delivery.setStatus(NotificationDelivery.Status.FAILED);
                log.warn("Notification {} permanently failed: {}", id, error);
            }
        }
        repository.save(delivery);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) return "Unknown delivery error";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
