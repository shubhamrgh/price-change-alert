package com.pricechangealert.service;

import com.pricechangealert.model.AlertLog;
import com.pricechangealert.model.Market;
import com.pricechangealert.model.Quote;
import com.pricechangealert.model.WatchItem;
import com.pricechangealert.repository.AlertLogRepository;
import com.pricechangealert.repository.WatchItemRepository;
import com.pricechangealert.source.PriceService;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alert engine. Every poll tick: fetch each active watch item's latest price,
 * compare against threshold / previous price, log + push when triggered.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final WatchItemRepository watchItemRepository;
    private final AlertLogRepository alertLogRepository;
    private final PriceService priceService;
    private final PushService pushService;

    public AlertService(WatchItemRepository watchItemRepository,
                        AlertLogRepository alertLogRepository,
                        PriceService priceService,
                        PushService pushService) {
        this.watchItemRepository = watchItemRepository;
        this.alertLogRepository = alertLogRepository;
        this.priceService = priceService;
        this.pushService = pushService;
    }

    @Scheduled(fixedDelayString = "${price-change-alert.poll.interval-ms:30000}")
    @Transactional
    public void poll() {
        List<WatchItem> items = watchItemRepository.findAll().stream()
                .filter(WatchItem::isActive)
                .toList();
        if (items.isEmpty()) return;

        int fetched = 0;
        for (WatchItem item : items) {
            Optional<Quote> quoteOpt = priceService.fetch(item.getSymbol(), item.getMarket(), item.getCurrency());
            if (quoteOpt.isEmpty()) continue;
            fetched++;
Quote q = quoteOpt.get();
            double previous = item.getLastPrice() == null ? q.price() : item.getLastPrice();

            // Re-alert only on new movement: for PRICE alerts require a further
            // drop/rise of >= 0.5% against the last alerted price, so an item sitting
            // on the wrong side of the threshold doesn't spam.
            boolean triggered = item.alertTriggered(q.price(), previous);
            boolean shouldAlert = false;
            if (triggered) {
                boolean belowEdge = !"ABOVE".equalsIgnoreCase(item.getDirection());
                double lastAlertedPrice = item.getLastAlertedPrice() == null
                        ? (belowEdge ? Double.MAX_VALUE : Double.MIN_VALUE)
                        : item.getLastAlertedPrice();
                shouldAlert = belowEdge
                        ? q.price() < lastAlertedPrice * 0.995
                        : q.price() > lastAlertedPrice * 1.005;
            }

            if (shouldAlert) {
                String message = buildMessage(item, q);
                AlertLog entry = new AlertLog();
                entry.setOwnerId(item.getOwnerId());
                entry.setSymbol(item.getSymbol());
                entry.setMarket(item.getMarket().name());
                entry.setMessage(message);
                entry.setPrice(q.price());
                alertLogRepository.save(entry);
                item.setLastAlertedAt(java.time.Instant.now());
                item.setLastAlertedPrice(q.price());
                item.setActive(false);
                pushService.notifyAll(item.getOwnerId(), item.getSymbol(), item.getMarket(), message);
                log.info("ALERT {} {} (watch item paused)", item.getSymbol(), message);
            }

            item.setPreviousPrice(previous);
            item.setLastPrice(q.price());
            item.setLastSource(q.source());
            item.setLastFetchedAt(q.fetchedAt());
            watchItemRepository.save(item);
        }
        log.debug("Polled {} quotes ({} active items)", fetched, items.size());
    }

    private String buildMessage(WatchItem item, Quote q) {
        String cur = item.getCurrency() == null ? "INR" : item.getCurrency().toUpperCase();
        String fmt = "USD".equals(cur) ? "$%,.2f" : "â‚¹%,.2f";
        if ("PERCENT".equals(item.getTriggerType())) {
            if ("ABOVE".equalsIgnoreCase(item.getDirection())) {
                return String.format("%s rose %.2f%% (now " + fmt + ")", item.getSymbol(), item.getThresholdValue(), q.price());
            }
            return String.format("%s dropped %.2f%% (now " + fmt + ")", item.getSymbol(), item.getThresholdValue(), q.price());
        }
        if ("ABOVE".equalsIgnoreCase(item.getDirection())) {
            return String.format("%s rose to " + fmt + " (above " + fmt + ")", item.getSymbol(), q.price(), item.getThresholdValue());
        }
        return String.format("%s fell to " + fmt + " (below " + fmt + ")", item.getSymbol(), q.price(), item.getThresholdValue());
    }
}

