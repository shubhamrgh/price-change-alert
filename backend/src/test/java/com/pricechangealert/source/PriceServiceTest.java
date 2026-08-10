package com.pricechangealert.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pricechangealert.cache.ApplicationCaches;
import com.pricechangealert.cache.CacheProperties;
import com.pricechangealert.model.Market;
import com.pricechangealert.model.Quote;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PriceServiceTest {

    @Test
    void normalizesKeysAndCachesDuplicateQuoteRequests() {
        AtomicInteger calls = new AtomicInteger();
        PriceProvider provider = new PriceProvider() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public boolean supports(Market market) {
                return market == Market.CRYPTO;
            }

            @Override
            public Optional<Quote> fetch(String symbol, Market market, String currency) {
                calls.incrementAndGet();
                return Optional.of(new Quote(
                        market, symbol, "Bitcoin", 100, currency.toUpperCase(), name(), Instant.now()));
            }
        };
        PriceService service = new PriceService(
                List.of(provider), new ApplicationCaches(new CacheProperties()));

        Optional<Quote> first = service.fetch(" btc ", Market.CRYPTO, "USD");
        Optional<Quote> second = service.fetch("BTC", Market.CRYPTO, "usd");

        assertTrue(first.isPresent());
        assertEquals(first, second);
        assertEquals(1, calls.get());
    }
}
