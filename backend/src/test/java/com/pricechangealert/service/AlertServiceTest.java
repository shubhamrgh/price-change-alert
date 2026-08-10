package com.pricechangealert.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pricechangealert.model.Market;
import com.pricechangealert.model.Quote;
import com.pricechangealert.model.WatchItem;
import com.pricechangealert.repository.AlertLogRepository;
import com.pricechangealert.repository.WatchItemRepository;
import com.pricechangealert.source.PriceService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AlertServiceTest {

    @Test
    void staleFallbackQuoteCannotTriggerAnAlert() {
        WatchItemRepository watchItems = mock(WatchItemRepository.class);
        PriceService prices = mock(PriceService.class);
        AlertItemProcessor processor = mock(AlertItemProcessor.class);
        PushService push = mock(PushService.class);
        WatchItem item = watchItem();
        when(watchItems.findAllByActiveTrueOrderByIdAsc()).thenReturn(List.of(item));
        when(prices.fetch("RELIANCE", Market.NSE, "INR")).thenReturn(Optional.of(
                new Quote(Market.NSE, "RELIANCE", "Reliance Industries",
                        90, "INR", "cached", Instant.now().minus(Duration.ofMinutes(3)))));
        AlertService service = new AlertService(
                watchItems, prices, processor, push, Duration.ofMinutes(2));

        service.poll();

        verifyNoInteractions(processor, push);
    }

    @Test
    void pausesWatchItemAfterAlertIsTriggered() {
        WatchItemRepository watchItems = mock(WatchItemRepository.class);
        AlertLogRepository alertLogs = mock(AlertLogRepository.class);
        AlertItemProcessor processor = new AlertItemProcessor(watchItems, alertLogs);

        WatchItem item = watchItem();
        when(watchItems.findByIdForUpdate(1L)).thenReturn(Optional.of(item));

        Optional<AlertItemProcessor.TriggeredAlert> result = processor.process(1L,
                new Quote(Market.NSE, "RELIANCE", "Reliance Industries",
                        90, "INR", "yahoo", Instant.now()));

        assertTrue(result.isPresent());
        assertFalse(item.isActive());
        verify(alertLogs).save(any());
        verify(watchItems).save(item);
    }

    private static WatchItem watchItem() {
        WatchItem item = new WatchItem();
        item.setId(1L);
        item.setOwnerId("visitor");
        item.setSymbol("RELIANCE");
        item.setName("Reliance Industries");
        item.setMarket(Market.NSE);
        item.setCurrency("INR");
        item.setDirection("BELOW");
        item.setTriggerType("PRICE");
        item.setThresholdValue(100);
        item.setLastPrice(110.0);
        item.setActive(true);
        return item;
    }
}
