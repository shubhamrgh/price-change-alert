package com.pricechangealert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pricechangealert.cache.ApplicationCaches;
import com.pricechangealert.cache.CacheProperties;
import com.pricechangealert.model.Chart;
import com.pricechangealert.model.Market;
import com.pricechangealert.source.CoinGeckoProvider;
import com.pricechangealert.source.YahooProvider;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChartServiceTest {

    @Test
    void cachesNormalizedChartRequests() {
        CoinGeckoProvider coinGecko = mock(CoinGeckoProvider.class);
        YahooProvider yahoo = mock(YahooProvider.class);
        List<double[]> points = List.of(new double[]{1, 100}, new double[]{2, 105});
        when(yahoo.history("RELIANCE", Market.NSE, 30, "inr"))
                .thenReturn(Optional.of(points));
        ChartService service = new ChartService(
                coinGecko, yahoo, new ApplicationCaches(new CacheProperties()));

        Optional<Chart> first = service.chart(" reliance ", Market.NSE, 30, "INR");
        Optional<Chart> second = service.chart("RELIANCE", Market.NSE, 30, "inr");

        assertTrue(first.isPresent());
        assertEquals(first, second);
        verify(yahoo).history("RELIANCE", Market.NSE, 30, "inr");
    }
}
