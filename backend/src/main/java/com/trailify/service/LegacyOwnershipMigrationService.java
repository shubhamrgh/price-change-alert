package com.trailify.service;

import com.trailify.model.AlertLog;
import com.trailify.model.PushSubscription;
import com.trailify.model.WatchItem;
import com.trailify.repository.AlertLogRepository;
import com.trailify.repository.PushSubscriptionRepository;
import com.trailify.repository.WatchItemRepository;
import com.trailify.service.notification.NotificationPreferenceService;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Claims data created by the previous visitor-token ownership model. */
@Service
public class LegacyOwnershipMigrationService {

    private static final Logger log = LoggerFactory.getLogger(LegacyOwnershipMigrationService.class);
    private static final Pattern SAFE_OWNER = Pattern.compile("[A-Za-z0-9_-]{6,64}");

    private final WatchItemRepository watchItems;
    private final AlertLogRepository alerts;
    private final PushSubscriptionRepository pushSubscriptions;
    private final NotificationPreferenceService preferences;

    public LegacyOwnershipMigrationService(WatchItemRepository watchItems,
                                           AlertLogRepository alerts,
                                           PushSubscriptionRepository pushSubscriptions,
                                           NotificationPreferenceService preferences) {
        this.watchItems = watchItems;
        this.alerts = alerts;
        this.pushSubscriptions = pushSubscriptions;
        this.preferences = preferences;
    }

    @Transactional
    public void claim(String legacyOwnerId, String userId) {
        if (legacyOwnerId == null || legacyOwnerId.isBlank()) return;
        String previousOwner = legacyOwnerId.trim();
        if (!SAFE_OWNER.matcher(previousOwner).matches() || previousOwner.equals(userId)) return;

        List<WatchItem> ownedWatchItems = watchItems.findAllOwnedBy(previousOwner);
        ownedWatchItems.forEach(item -> item.setOwnerId(userId));
        watchItems.saveAll(ownedWatchItems);

        List<AlertLog> ownedAlerts = alerts.findRecentOwnedBy(previousOwner);
        ownedAlerts.forEach(alert -> alert.setOwnerId(userId));
        alerts.saveAll(ownedAlerts);

        List<PushSubscription> ownedSubscriptions = pushSubscriptions.findAllOwnedBy(previousOwner);
        ownedSubscriptions.forEach(subscription -> subscription.setOwnerId(userId));
        pushSubscriptions.saveAll(ownedSubscriptions);
        if (!ownedSubscriptions.isEmpty()) preferences.setWebPush(userId, true);

        int claimed = ownedWatchItems.size() + ownedAlerts.size() + ownedSubscriptions.size();
        if (claimed > 0) log.info("Claimed {} legacy records for account {}", claimed, userId);
    }
}
