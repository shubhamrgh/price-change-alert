package com.pricechangealert.source;

import com.pricechangealert.model.Market;
import com.pricechangealert.model.Quote;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Chains providers into an ordered fallback per market:
 *   NSE    -> yahoo (.NS)
 *   BSE    -> yahoo (.BO)
 *   CRYPTO -> coingecko, yahoo (-INR / -USD)
 */
@Service
public class PriceService {

    private final Map<Market, List<PriceProvider>> fallback = new EnumMap<>(Market.class);

    public PriceService(List<PriceProvider> providers) {
        fallback.put(Market.CRYPTO, providers.stream().filter(p -> p.supports(Market.CRYPTO)).toList());
        fallback.put(Market.NSE, providers.stream().filter(p -> p.supports(Market.NSE)).toList());
        fallback.put(Market.BSE, providers.stream().filter(p -> p.supports(Market.BSE)).toList());
    }

    /** First provider in the chain that answers (batches the fallback list). */
    public Optional<Quote> fetch(String symbol, Market market, String currency) {
        String cur = currency == null || currency.isBlank() ? "inr" : currency.trim().toLowerCase();
        for (PriceProvider provider : fallback.getOrDefault(market, List.of())) {
            Optional<Quote> q = provider.fetch(symbol, market, cur);
            if (q.isPresent()) return q;
        }
        return Optional.empty();
    }
}
