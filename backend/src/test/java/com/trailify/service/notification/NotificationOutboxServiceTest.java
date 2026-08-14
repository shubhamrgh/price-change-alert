package com.trailify.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trailify.model.Market;
import com.trailify.model.NotificationChannel;
import com.trailify.model.NotificationDelivery;
import com.trailify.model.NotificationPreference;
import com.trailify.repository.NotificationDeliveryRepository;
import com.trailify.repository.NotificationPreferenceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationOutboxServiceTest {

    @Test
    void queuesOneIndependentDeliveryPerEnabledDestination() {
        NotificationPreferenceRepository preferences = mock(NotificationPreferenceRepository.class);
        NotificationDeliveryRepository deliveries = mock(NotificationDeliveryRepository.class);
        when(preferences.findAllByUserIdAndEnabledTrue("user-id")).thenReturn(List.of(
                preference(NotificationChannel.EMAIL, "person@example.com"),
                preference(NotificationChannel.TELEGRAM, "123456")));
        NotificationOutboxService service = new NotificationOutboxService(preferences, deliveries);

        service.enqueue("user-id", "BTC", Market.CRYPTO, "BTC fell below $50,000");

        ArgumentCaptor<NotificationDelivery> queued = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveries, org.mockito.Mockito.times(2)).save(queued.capture());
        assertEquals(List.of(NotificationChannel.EMAIL, NotificationChannel.TELEGRAM),
                queued.getAllValues().stream().map(NotificationDelivery::getChannel).toList());
        assertEquals("user-id", queued.getAllValues().getFirst().getOwnerId());
        assertEquals(NotificationDelivery.Status.PENDING, queued.getAllValues().getFirst().getStatus());
    }

    private static NotificationPreference preference(NotificationChannel channel, String destination) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId("user-id");
        preference.setChannel(channel);
        preference.setDestination(destination);
        preference.setEnabled(true);
        return preference;
    }
}
