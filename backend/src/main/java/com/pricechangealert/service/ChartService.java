package com.pricechangealert.service;

import com.pricechangealert.cache.ApplicationCaches;
import com.pricechangealert.cache.ApplicationCaches.ChartKey;
import com.pricechangealert.model.Chart;
import com.pricechangealert.model.Market;
import com.pricechangealert.source.CoinGeckoProvider;
import com.pricechangealert.source.YahooProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** History for the chart endpoint: crypto from CoinGecko, stocks from Yahoo. */
@Service
public class ChartService {

    private final CoinGeckoProvider coinGecko;
    private final YahooProvider yahoo;
    private final ApplicationCaches caches;

    public ChartService(CoinGeckoProvider coinGecko, YahooProvider yahoo, ApplicationCaches caches) {
        this.coinGecko = coinGecko;
        this.yahoo = yahoo;
        this.caches = caches;
    }

    public Optional<Chart> chart(String symbol, Market market, int days, String currency) {
        if (market == null || symbol == null || symbol.isBlank()) return Optional.empty();
        int boundedDays = Math.max(1, Math.min(days, 365));
        ChartKey key = new ChartKey(market, symbol, boundedDays, currency);
        return caches.chart(key,
                () -> build(key.symbol(), key.market(), key.days(), key.currency()));
    }

    private Optional<Chart> build(String symbol, Market market, int days, String currency) {
        boolean usd = market == Market.CRYPTO && "usd".equals(currency);
        String label = usd ? "USD" : "INR";
        if (market == Market.CRYPTO) {
            Optional<List<double[]>> cg = coinGecko.marketChart(symbol, days, currency);
            if (cg.isPresent() && !cg.get().isEmpty()) {
                return Optional.of(new Chart(cg.get(), "coingecko", label));
            }
        }
        Optional<List<double[]>> yh = yahoo.history(symbol, market, days, currency);
        if (yh.isPresent() && !yh.get().isEmpty()) {
            return Optional.of(new Chart(yh.get(), "yahoo", label));
        }
        return Optional.empty();
    }
}
