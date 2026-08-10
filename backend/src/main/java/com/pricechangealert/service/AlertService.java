package com.pricechangealert.service;

import com.pricechangealert.model.Quote;
import com.pricechangealert.model.WatchItem;
import com.pricechangealert.repository.WatchItemRepository;
import com.pricechangealert.service.AlertItemProcessor.TriggeredAlert;
import com.pricechangealert.source.PriceService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Fetches active prices outside database transactions, then applies each result in an isolated
 * transaction so a slow provider or failed item cannot hold up the whole watchlist.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final WatchItemRepository watchItemRepository;
    private final PriceService priceService;
    private final AlertItemProcessor itemProcessor;
    private final PushService pushService;
    private final Duration maxQuoteAge;

    public AlertService(WatchItemRepository watchItemRepository,
                        PriceService priceService,
                        AlertItemProcessor itemProcessor,
                        PushService pushService,
                        @Value("${price-change-alert.poll.max-quote-age:2m}") Duration maxQuoteAge) {
        this.watchItemRepository = watchItemRepository;
        this.priceService = priceService;
        this.itemProcessor = itemProcessor;
        this.pushService = pushService;
        this.maxQuoteAge = maxQuoteAge;
    }

    @Scheduled(fixedDelayString = "${price-change-alert.poll.interval-ms:30000}")
    public void poll() {
        List<WatchItem> items = watchItemRepository.findAllByActiveTrueOrderByIdAsc();
        if (items.isEmpty()) return;

        int fetched = 0;
        for (WatchItem item : items) {
            try {
                Optional<Quote> quote = priceService.fetch(
                        item.getSymbol(), item.getMarket(), item.getCurrency());
                if (quote.isEmpty()) continue;
                if (!isFreshEnough(quote.get())) {
                    log.debug("Ignoring stale quote for {} {} fetched at {}",
                            item.getMarket(), item.getSymbol(), quote.get().fetchedAt());
                    continue;
                }
                fetched++;
                itemProcessor.process(item.getId(), quote.get()).ifPresent(this::sendNotification);
            } catch (RuntimeException exception) {
                log.warn("Alert poll failed for watch item {} ({} {})",
                        item.getId(), item.getMarket(), item.getSymbol(), exception);
            }
        }
        log.debug("Polled {} quotes ({} active items)", fetched, items.size());
    }

    private boolean isFreshEnough(Quote quote) {
        return quote.fetchedAt() != null
                && !quote.fetchedAt().isBefore(Instant.now().minus(maxQuoteAge));
    }

    private void sendNotification(TriggeredAlert alert) {
        try {
            pushService.notifyAll(alert.ownerId(), alert.symbol(), alert.market(), alert.message());
        } catch (RuntimeException exception) {
            log.warn("Alert was persisted but push delivery failed for {} {}",
                    alert.market(), alert.symbol(), exception);
        }
        log.info("ALERT {} {} (watch item paused)", alert.symbol(), alert.message());
    }
}
