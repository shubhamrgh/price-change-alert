package com.pricedrop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricedrop.model.Market;
import com.pricedrop.model.PushSubscription;
import com.pricedrop.repository.PushSubscriptionRepository;
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

    public void saveSubscription(String ownerId, String endpoint, String p256dh, String auth) {
        PushSubscription sub = repository.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
        sub.setOwnerId(ownerId);
        sub.setEndpoint(endpoint);
        sub.setP256dh(p256dh);
        sub.setAuth(auth);
        repository.save(sub);
        log.info("Saved push subscription: {}", endpoint);
    }

    @Transactional
    public void removeSubscription(String ownerId, String endpoint) {
        repository.findByEndpoint(endpoint)
                .filter(sub -> ownerId.equals(sub.getOwnerId()))
                .ifPresent(repository::delete);
        log.info("Removed push subscription: {}", endpoint);
    }

    public void notifyAll(String ownerId, String symbol, Market market, String message) {
        if (webPush == null) return;
        List<PushSubscription> subs = repository.findAllOwnedBy(ownerId);
        if (subs.isEmpty()) return;
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
        for (PushSubscription sub : subs) {
            try {
                nl.martijndwars.webpush.Subscription target = new nl.martijndwars.webpush.Subscription(
                        sub.getEndpoint(),
                        new nl.martijndwars.webpush.Subscription.Keys(sub.getP256dh(), sub.getAuth()));
                Notification notification = new Notification(target, payload);
                webPush.send(notification);
                log.info("Push sent to {}", sub.getEndpoint());
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                if (msg.contains("410") || msg.contains("404")) {
                    log.warn("Subscription expired, removing: {}", sub.getEndpoint());
                    repository.delete(sub);
                } else {
                    log.warn("Push failed for {}: {}", sub.getEndpoint(), msg);
                }
            }
        }
    }
}
