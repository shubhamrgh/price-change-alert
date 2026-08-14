package com.trailify.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trailify.model.Quote;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CoinbaseProviderTest {

    @Test
    void parsesSpotPriceResponse() {
        CoinbaseProvider provider = new CoinbaseProvider();

        Optional<Quote> result = provider.parseQuote(
                "{\"data\":{\"amount\":\"63976.725\",\"base\":\"BTC\",\"currency\":\"USD\"}}",
                "BTC", "USD");

        assertTrue(result.isPresent());
        assertEquals(63_976.725, result.orElseThrow().price());
        assertEquals("coinbase", result.orElseThrow().source());
    }
}
