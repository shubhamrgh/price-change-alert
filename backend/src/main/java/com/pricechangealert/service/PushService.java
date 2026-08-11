package com.pricechangealert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricechangealert.model.Market;
import com.pricechangealert.model.PushSubscription;
import com.pricechangealert.repository.PushSubscriptionRepository;
import com.pricechangealert.service.notification.DeliveryResult;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import nl.martijndwars.webpush.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Web Push (RFC 8030) via VAPID. Requires the app to be served over HTTPS
 * (or localhost) and the PWA to have requested permission + POSTed its subscription.
 * Subscription.failure handling: a 410/404 means the subscription is dead -> removed.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final PushSubscriptionRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();
    private final nl.martijndwars.webpush.PushService webPush;
    private final String vapidPublicKeyB64;

    public PushService(PushSubscriptionRepository repository,
                       @Value("${price-change-alert.vapid.public-key}") String publicKey,
                       @Value("${price-change-alert.vapid.private-key}") String privateKey,
                       @Value("${price-change-alert.vapid.subject}") String subject) {
        this.repository = repository;
        this.vapidPublicKeyB64 = publicKey;
        if (publicKey == null || publicKey.isBlank() || privateKey == null || privateKey.isBlank()) {
            this.webPush = null;
            log.warn("Web Push is disabled: PRICE_CHANGE_ALERT_VAPID_PUBLIC_KEY and PRICE_CHANGE_ALERT_VAPID_PRIVATE_KEY are not configured");
            return;
        }
        try {
            this.webPush = new nl.martijndwars.webpush.PushService(publicKey, privateKey);
            this.webPush.setSubject(subject);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Invalid VAPID key configuration", e);
        }
    }

    public String vapidPublicKey() {
        if (webPush == null) throw new IllegalStateException("Web Push is not configured on this deployment");
        return vapidPublicKeyB64;
    }

    public boolean available() {
        return webPush != null;
    }

    public void saveSubscription(String ownerId, String endpoint, String p256dh, String auth) {
        PushSubscription sub = repository.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
        sub.setOwnerId(ownerId);
        sub.setEndpoint(endpoint);
        sub.setP256dh(p256dh);
        sub.setAuth(auth);
        PushSubscription saved = repository.save(sub);
        log.info("Saved push subscription {}", saved.getId());
    }

    @Transactional
    public boolean removeSubscription(String ownerId, String endpoint) {
        List<PushSubscription> owned = repository.findAllOwnedBy(ownerId);
        owned.stream().filter(sub -> endpoint.equals(sub.getEndpoint())).findFirst()
                .ifPresent(repository::delete);
        boolean hasRemaining = owned.stream().anyMatch(sub -> !endpoint.equals(sub.getEndpoint()));
        log.info("Removed push subscription; owner still has active devices: {}", hasRemaining);
        return hasRemaining;
    }

    public DeliveryResult notifyAll(String ownerId, String symbol, Market market, String message) {
        if (webPush == null) return DeliveryResult.failed("Web Push is not configured");
        List<PushSubscription> subs = repository.findAllOwnedBy(ownerId);
        if (subs.isEmpty()) return DeliveryResult.failed("No active Web Push subscription");
        String payload;
        try {
            payload = mapper.writeValueAsString(java.util.Map.of(
                    "title", "Price Change Alert: " + symbol.toUpperCase(Locale.ROOT),
                    "body", message,
                    "market", market.name(),
                    "url", "/#alerts"));
        } catch (Exception e) {
            payload = null;
        }
        int delivered = 0;
        boolean transientFailure = false;
        for (PushSubscription sub : subs) {
            try {
                nl.martijndwars.webpush.Subscription target = new nl.martijndwars.webpush.Subscription(
                        sub.getEndpoint(),
                        new nl.martijndwars.webpush.Subscription.Keys(sub.getP256dh(), sub.getAuth()));
                Notification notification = new Notification(target, payload);
                webPush.send(notification);
                delivered++;
                log.info("Push sent to subscription {}", sub.getId());
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                if (msg.contains("410") || msg.contains("404")) {
                    log.warn("Subscription {} expired, removing", sub.getId());
                    repository.delete(sub);
                } else {
                    transientFailure = true;
                    log.warn("Push failed for subscription {}: {}", sub.getId(), msg);
                }
            }
        }
        if (delivered > 0) return DeliveryResult.sent();
        return transientFailure
                ? DeliveryResult.retry("All Web Push endpoints failed temporarily")
                : DeliveryResult.failed("No valid Web Push subscriptions remain");
    }
}

