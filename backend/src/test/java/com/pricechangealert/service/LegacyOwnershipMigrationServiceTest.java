package com.pricechangealert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pricechangealert.model.AlertLog;
import com.pricechangealert.model.PushSubscription;
import com.pricechangealert.model.WatchItem;
import com.pricechangealert.repository.AlertLogRepository;
import com.pricechangealert.repository.PushSubscriptionRepository;
import com.pricechangealert.repository.WatchItemRepository;
import com.pricechangealert.service.notification.NotificationPreferenceService;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyOwnershipMigrationServiceTest {

    @Test
    void claimsPreviousVisitorDataForAuthenticatedAccount() {
        WatchItemRepository watchItems = mock(WatchItemRepository.class);
        AlertLogRepository alerts = mock(AlertLogRepository.class);
        PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
        NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
        WatchItem watchItem = new WatchItem();
        AlertLog alert = new AlertLog();
        PushSubscription subscription = new PushSubscription();
        when(watchItems.findAllOwnedBy("visitor-token")).thenReturn(List.of(watchItem));
        when(alerts.findRecentOwnedBy("visitor-token")).thenReturn(List.of(alert));
        when(subscriptions.findAllOwnedBy("visitor-token")).thenReturn(List.of(subscription));
        LegacyOwnershipMigrationService service = new LegacyOwnershipMigrationService(
                watchItems, alerts, subscriptions, preferences);

        service.claim("visitor-token", "account-id");

        assertEquals("account-id", watchItem.getOwnerId());
        assertEquals("account-id", alert.getOwnerId());
        assertEquals("account-id", subscription.getOwnerId());
        verify(preferences).setWebPush("account-id", true);
    }
}
