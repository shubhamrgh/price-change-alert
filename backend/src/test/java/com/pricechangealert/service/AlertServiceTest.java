package com.pricechangealert.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pricechangealert.model.Market;
import com.pricechangealert.model.Quote;
import com.pricechangealert.model.WatchItem;
import com.pricechangealert.repository.AlertLogRepository;
import com.pricechangealert.repository.WatchItemRepository;
import com.pricechangealert.source.PriceService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AlertServiceTest {

    @Test
    void pausesWatchItemAfterAlertIsTriggered() {
        WatchItemRepository watchItems = mock(WatchItemRepository.class);
        AlertLogRepository alertLogs = mock(AlertLogRepository.class);
        PriceService prices = mock(PriceService.class);
        PushService push = mock(PushService.class);
        AlertService service = new AlertService(watchItems, alertLogs, prices, push);

        WatchItem item = new WatchItem();
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

        when(watchItems.findAll()).thenReturn(List.of(item));
        when(prices.fetch("RELIANCE", Market.NSE, "INR")).thenReturn(Optional.of(
                new Quote(Market.NSE, "RELIANCE", "Reliance Industries", 90, "INR", "yahoo", Instant.now())));

        service.poll();

        assertFalse(item.isActive());
        verify(alertLogs).save(any());
        verify(watchItems).save(item);
        verify(push).notifyAll(anyString(), anyString(), any(), anyString());
    }
}
