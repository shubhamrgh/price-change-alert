package com.pricechangealert.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pricechangealert.model.NotificationChannel;
import com.pricechangealert.model.NotificationPreference;
import com.pricechangealert.model.UserAccount;
import com.pricechangealert.repository.NotificationPreferenceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationPreferenceServiceTest {

    @Test
    void discordDestinationIsRestrictedToOfficialWebhookHosts() {
        NotificationPreferenceRepository repository = mock(NotificationPreferenceRepository.class);
        NotificationSender discord = mock(NotificationSender.class);
        when(discord.channel()).thenReturn(NotificationChannel.DISCORD);
        when(discord.available()).thenReturn(true);
        NotificationPreferenceService service = new NotificationPreferenceService(repository, List.of(discord));
        UserAccount user = user();
        when(repository.findByUserIdAndChannel(user.getId(), NotificationChannel.DISCORD))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.update(user,
                NotificationChannel.DISCORD, true, "https://example.com/api/webhooks/stolen"));

        service.update(user, NotificationChannel.DISCORD, true,
                "https://discord.com/api/webhooks/123/token");
        ArgumentCaptor<NotificationPreference> saved = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(repository).save(saved.capture());
        assertEquals("https://discord.com/api/webhooks/123/token", saved.getValue().getDestination());
    }

    private static UserAccount user() {
        UserAccount user = new UserAccount();
        user.setId("1b434f1d-792d-475b-9714-4a43f2057fb4");
        user.setEmail("person@example.com");
        return user;
    }
}
