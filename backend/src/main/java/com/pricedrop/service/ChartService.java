package com.pricedrop.service;

import com.pricedrop.model.Chart;
import com.pricedrop.model.Market;
import com.pricedrop.source.CoinGeckoProvider;
import com.pricedrop.source.YahooProvider;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** History for the chart endpoint: crypto from CoinGecko, stocks from Yahoo; cached ~5 min. */
@Service
public class ChartService {

    private record CacheKey(String symbol, Market market, int days, String currency) {
    }

    private static final long TTL_MS = 5 * 60 * 1000;

    private final CoinGeckoProvider coinGecko;
    private final YahooProvider yahoo;
    private final Map<CacheKey, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(Chart chart, long fetchedAt) {
    }

    public ChartService(CoinGeckoProvider coinGecko, YahooProvider yahoo) {
        this.coinGecko = coinGecko;
        this.yahoo = yahoo;
    }

    public Optional<Chart> chart(String symbol, Market market, int days, String currency) {
        String cur = currency == null || currency.isBlank() ? "inr" : currency.trim().toLowerCase();
        CacheKey key = new CacheKey(symbol.trim().toUpperCase(), market, days, cur);
        Cached hit = cache.get(key);
        if (hit != null && System.currentTimeMillis() - hit.fetchedAt() < TTL_MS) {
            return Optional.of(hit.chart());
        }
        Optional<Chart> built = build(symbol, market, days, cur);
        built.ifPresent(c -> cache.put(key, new Cached(c, System.currentTimeMillis())));
        return built;
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