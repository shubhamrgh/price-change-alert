package com.pricechangealert.service;

import com.pricechangealert.model.AlertLog;
import com.pricechangealert.model.Market;
import com.pricechangealert.model.Quote;
import com.pricechangealert.model.WatchItem;
import com.pricechangealert.repository.AlertLogRepository;
import com.pricechangealert.repository.WatchItemRepository;
import com.pricechangealert.service.notification.NotificationOutboxService;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies one fetched quote to one watch item inside a short database transaction. */
@Service
public class AlertItemProcessor {

    public record TriggeredAlert(String ownerId, String symbol, Market market, String message) {
    }

    private final WatchItemRepository watchItemRepository;
    private final AlertLogRepository alertLogRepository;
    private final NotificationOutboxService notificationOutbox;

    public AlertItemProcessor(WatchItemRepository watchItemRepository,
                              AlertLogRepository alertLogRepository,
                              NotificationOutboxService notificationOutbox) {
        this.watchItemRepository = watchItemRepository;
        this.alertLogRepository = alertLogRepository;
        this.notificationOutbox = notificationOutbox;
    }

    @Transactional
    public Optional<TriggeredAlert> process(Long watchItemId, Quote quote) {
        if (watchItemId == null || quote == null) return Optional.empty();
        WatchItem item = watchItemRepository.findByIdForUpdate(watchItemId).orElse(null);
        if (item == null || !item.isActive() || item.getMarket() != quote.market()
                || !item.getSymbol().equalsIgnoreCase(quote.symbol())) {
            return Optional.empty();
        }

        double previous = item.getLastPrice() == null ? quote.price() : item.getLastPrice();
        boolean shouldAlert = shouldAlert(item, quote.price(), previous);
        Optional<TriggeredAlert> triggeredAlert = Optional.empty();

        if (shouldAlert) {
            String message = buildMessage(item, quote);
            AlertLog entry = new AlertLog();
            entry.setOwnerId(item.getOwnerId());
            entry.setSymbol(item.getSymbol());
            entry.setMarket(item.getMarket().name());
            entry.setMessage(message);
            entry.setPrice(quote.price());
            alertLogRepository.save(entry);
            notificationOutbox.enqueue(item.getOwnerId(), item.getSymbol(), item.getMarket(), message);

            item.setLastAlertedAt(Instant.now());
            item.setLastAlertedPrice(quote.price());
            item.setActive(false);
            triggeredAlert = Optional.of(new TriggeredAlert(
                    item.getOwnerId(), item.getSymbol(), item.getMarket(), message));
        }

        item.setPreviousPrice(previous);
        item.setLastPrice(quote.price());
        item.setLastSource(quote.source());
        item.setLastFetchedAt(quote.fetchedAt());
        watchItemRepository.save(item);
        return triggeredAlert;
    }

    private static boolean shouldAlert(WatchItem item, double currentPrice, double previousPrice) {
        if (!item.alertTriggered(currentPrice, previousPrice)) return false;
        boolean belowEdge = !"ABOVE".equalsIgnoreCase(item.getDirection());
        double lastAlertedPrice = item.getLastAlertedPrice() == null
                ? (belowEdge ? Double.MAX_VALUE : Double.MIN_VALUE)
                : item.getLastAlertedPrice();
        return belowEdge
                ? currentPrice < lastAlertedPrice * 0.995
                : currentPrice > lastAlertedPrice * 1.005;
    }

    private static String buildMessage(WatchItem item, Quote quote) {
        String currency = item.getCurrency().toUpperCase(Locale.ROOT);
        String priceFormat = "USD".equals(currency) ? "$%,.2f" : "\u20B9%,.2f";
        if ("PERCENT".equals(item.getTriggerType())) {
            String movement = "ABOVE".equalsIgnoreCase(item.getDirection()) ? "rose" : "dropped";
            return String.format(Locale.ROOT, "%s %s %.2f%% (now " + priceFormat + ")",
                    item.getSymbol(), movement, item.getThresholdValue(), quote.price());
        }
        if ("ABOVE".equalsIgnoreCase(item.getDirection())) {
            return String.format(Locale.ROOT,
                    "%s rose to " + priceFormat + " (above " + priceFormat + ")",
                    item.getSymbol(), quote.price(), item.getThresholdValue());
        }
        return String.format(Locale.ROOT,
                "%s fell to " + priceFormat + " (below " + priceFormat + ")",
                item.getSymbol(), quote.price(), item.getThresholdValue());
    }
}
