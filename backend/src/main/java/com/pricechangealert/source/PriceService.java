package com.pricechangealert.source;

import com.pricechangealert.cache.ApplicationCaches;
import com.pricechangealert.cache.ApplicationCaches.QuoteKey;
import com.pricechangealert.model.Market;
import com.pricechangealert.model.Quote;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Chains providers into an ordered fallback per market:
 *   NSE    -> yahoo (.NS)
 *   BSE    -> yahoo (.BO)
 *   CRYPTO -> coingecko, yahoo (-INR / -USD)
 */
@Service
public class PriceService {

    private static final Logger log = LoggerFactory.getLogger(PriceService.class);

    private final Map<Market, List<PriceProvider>> fallback = new EnumMap<>(Market.class);
    private final ApplicationCaches caches;

    public PriceService(List<PriceProvider> providers, ApplicationCaches caches) {
        this.caches = caches;
        fallback.put(Market.CRYPTO, providers.stream().filter(p -> p.supports(Market.CRYPTO)).toList());
        fallback.put(Market.NSE, providers.stream().filter(p -> p.supports(Market.NSE)).toList());
        fallback.put(Market.BSE, providers.stream().filter(p -> p.supports(Market.BSE)).toList());
    }

    /**
     * Returns the first provider response, sharing duplicate lookups through a short-lived,
     * bounded cache. Provider failures remain isolated so the rest of the fallback chain runs.
     */
    public Optional<Quote> fetch(String symbol, Market market, String currency) {
        if (market == null || symbol == null || symbol.isBlank()) return Optional.empty();
        String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        String cur = currency == null || currency.isBlank()
                ? "inr"
                : currency.trim().toLowerCase(Locale.ROOT);
        QuoteKey key = new QuoteKey(market, normalizedSymbol, cur);
        return caches.quote(key, () -> fetchFromProviders(key));
    }

    private Optional<Quote> fetchFromProviders(QuoteKey key) {
        for (PriceProvider provider : fallback.getOrDefault(key.market(), List.of())) {
            try {
                Optional<Quote> quote = provider.fetch(key.symbol(), key.market(), key.currency());
                if (quote.isPresent()) return quote;
            } catch (RuntimeException exception) {
                log.debug("{} quote provider failed for {} on {}",
                        provider.name(), key.symbol(), key.market(), exception);
            }
        }
        return Optional.empty();
    }
}
