package com.pricechangealert.service.notification;

import com.pricechangealert.model.NotificationChannel;
import com.pricechangealert.model.NotificationPreference;
import com.pricechangealert.model.UserAccount;
import com.pricechangealert.repository.NotificationPreferenceRepository;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {

    public record ChannelView(NotificationChannel channel, boolean enabled, boolean available,
                              String destination, boolean destinationConfigured, String help) {
    }

    private static final Pattern TELEGRAM_DESTINATION =
            Pattern.compile("(?:-?[0-9]{1,20}|@[A-Za-z0-9_]{5,32})");

    private final NotificationPreferenceRepository repository;
    private final Map<NotificationChannel, NotificationSender> senders;

    public NotificationPreferenceService(NotificationPreferenceRepository repository,
                                         List<NotificationSender> notificationSenders) {
        this.repository = repository;
        this.senders = new EnumMap<>(NotificationChannel.class);
        notificationSenders.forEach(sender -> this.senders.put(sender.channel(), sender));
    }

    @Transactional(readOnly = true)
    public List<ChannelView> list(UserAccount user) {
        Map<NotificationChannel, NotificationPreference> current = new EnumMap<>(NotificationChannel.class);
        repository.findAllByUserIdOrderByChannel(user.getId())
                .forEach(preference -> current.put(preference.getChannel(), preference));
        return Arrays.stream(NotificationChannel.values())
                .map(channel -> view(user, channel, current.get(channel)))
                .toList();
    }

    @Transactional
    public ChannelView update(UserAccount user, NotificationChannel channel,
                              boolean enabled, String requestedDestination) {
        if (channel == NotificationChannel.WEB_PUSH) {
            throw new IllegalArgumentException("Use the mobile notification button to manage Web Push");
        }
        NotificationSender sender = senders.get(channel);
        if (enabled && (sender == null || !sender.available())) {
            throw new IllegalArgumentException(sender == null
                    ? "This notification channel is not installed"
                    : sender.availabilityMessage());
        }

        NotificationPreference preference = repository.findByUserIdAndChannel(user.getId(), channel)
                .orElseGet(() -> newPreference(user.getId(), channel));
        String destination = destination(user, channel, requestedDestination, preference.getDestination(), enabled);
        preference.setDestination(destination);
        preference.setEnabled(enabled);
        preference.setUpdatedAt(Instant.now());
        repository.save(preference);
        return view(user, channel, preference);
    }

    @Transactional
    public void setWebPush(String userId, boolean enabled) {
        NotificationPreference preference = repository
                .findByUserIdAndChannel(userId, NotificationChannel.WEB_PUSH)
                .orElseGet(() -> newPreference(userId, NotificationChannel.WEB_PUSH));
        preference.setDestination("browser");
        preference.setEnabled(enabled);
        preference.setUpdatedAt(Instant.now());
        repository.save(preference);
    }

    private ChannelView view(UserAccount user, NotificationChannel channel,
                             NotificationPreference preference) {
        NotificationSender sender = senders.get(channel);
        boolean available = sender != null && sender.available();
        String destination = preference == null ? "" : safeDestination(channel, preference.getDestination());
        if (channel == NotificationChannel.EMAIL) destination = user.getEmail();
        boolean configured = preference != null && preference.getDestination() != null
                && !preference.getDestination().isBlank();
        return new ChannelView(channel, preference != null && preference.isEnabled(), available,
                destination, configured, sender == null ? "Channel not installed" : sender.availabilityMessage());
    }

    private static NotificationPreference newPreference(String userId, NotificationChannel channel) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(userId);
        preference.setChannel(channel);
        return preference;
    }

    private static String destination(UserAccount user, NotificationChannel channel, String requested,
                                      String existing, boolean enabled) {
        if (channel == NotificationChannel.EMAIL) return user.getEmail();
        String value = requested == null ? "" : requested.trim();
        if (!enabled && value.isBlank()) return existing;
        if (enabled && value.isBlank() && existing != null && !existing.isBlank()) return existing;
        if (enabled && value.isBlank()) {
            throw new IllegalArgumentException("A destination is required for " + channel.name());
        }
        if (channel == NotificationChannel.TELEGRAM && !TELEGRAM_DESTINATION.matcher(value).matches()) {
            throw new IllegalArgumentException("Enter a Telegram chat ID or @channel username");
        }
        if (channel == NotificationChannel.DISCORD) validateDiscordWebhook(value);
        return value;
    }

    private static void validateDiscordWebhook(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            boolean approvedHost = "discord.com".equalsIgnoreCase(host)
                    || "discordapp.com".equalsIgnoreCase(host);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !approvedHost
                    || !uri.getPath().startsWith("/api/webhooks/")) {
                throw new IllegalArgumentException("Enter a valid Discord webhook URL");
            }
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException
                    && "Enter a valid Discord webhook URL".equals(exception.getMessage())) throw exception;
            throw new IllegalArgumentException("Enter a valid Discord webhook URL");
        }
    }

    private static String safeDestination(NotificationChannel channel, String destination) {
        if (destination == null) return "";
        if (channel != NotificationChannel.DISCORD) return destination;
        return ""; // webhook tokens are write-only in the API
    }
}
