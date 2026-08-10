package com.pricechangealert.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.pricechangealert.model.Chart;
import com.pricechangealert.model.Market;
import com.pricechangealert.model.Quote;
import com.pricechangealert.model.Suggestion;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Central bounded caches for remote market-data lookups.
 *
 * <p>Caffeine's atomic loader provides single-flight behavior: concurrent requests for the same
 * key share one provider call instead of consuming the upstream rate limit independently.</p>
 */
@Component
public class ApplicationCaches {

    public record QuoteKey(Market market, String symbol, String currency) {
        public QuoteKey {
            market = Objects.requireNonNull(market, "market");
            symbol = normalizeSymbol(symbol);
            currency = normalizeCurrency(currency);
        }
    }

    public record SearchKey(Market market, String query) {
        public SearchKey {
            market = Objects.requireNonNull(market, "market");
            query = normalizeQuery(query);
        }
    }

    public record ChartKey(Market market, String symbol, int days, String currency) {
        public ChartKey {
            market = Objects.requireNonNull(market, "market");
            symbol = normalizeSymbol(symbol);
            currency = normalizeCurrency(currency);
        }
    }

    public record LogoKey(Market market, String symbol) {
        public LogoKey {
            market = Objects.requireNonNull(market, "market");
            symbol = normalizeSymbol(symbol);
        }
    }

    private final Cache<QuoteKey, Optional<Quote>> quotes;
    private final Cache<SearchKey, List<Suggestion>> searches;
    private final Cache<ChartKey, Optional<Chart>> charts;
    private final Cache<LogoKey, Optional<URI>> logos;

    public ApplicationCaches(CacheProperties properties) {
        quotes = newCache(properties.getQuoteTtl(), properties.getQuoteNegativeTtl(),
                properties.getQuoteMaximumSize(), Optional::isEmpty);
        searches = newCache(properties.getSearchTtl(), properties.getSearchNegativeTtl(),
                properties.getSearchMaximumSize(), List::isEmpty);
        charts = newCache(properties.getChartTtl(), properties.getChartNegativeTtl(),
                properties.getChartMaximumSize(), Optional::isEmpty);
        logos = newCache(properties.getLogoTtl(), properties.getLogoNegativeTtl(),
                properties.getLogoMaximumSize(), Optional::isEmpty);
    }

    public Optional<Quote> quote(QuoteKey key, Supplier<Optional<Quote>> loader) {
        return quotes.get(key, ignored -> optional(loader.get()));
    }

    public List<Suggestion> search(SearchKey key, Supplier<List<Suggestion>> loader) {
        return searches.get(key, ignored -> List.copyOf(Objects.requireNonNullElseGet(loader.get(), List::of)));
    }

    public Optional<Chart> chart(ChartKey key, Supplier<Optional<Chart>> loader) {
        return charts.get(key, ignored -> optional(loader.get()));
    }

    public Optional<URI> logo(LogoKey key, Supplier<Optional<URI>> loader) {
        return logos.get(key, ignored -> optional(loader.get()));
    }

    Cache<QuoteKey, Optional<Quote>> quoteCache() {
        return quotes;
    }

    Cache<SearchKey, List<Suggestion>> searchCache() {
        return searches;
    }

    Cache<ChartKey, Optional<Chart>> chartCache() {
        return charts;
    }

    Cache<LogoKey, Optional<URI>> logoCache() {
        return logos;
    }

    private static <K, V> Cache<K, V> newCache(
            Duration ttl, Duration negativeTtl, long maximumSize, Predicate<V> isNegative) {
        if (!isPositive(ttl) || !isPositive(negativeTtl)) {
            throw new IllegalArgumentException("Cache TTL must be positive");
        }
        return Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfter(new Expiry<K, V>() {
                    @Override
                    public long expireAfterCreate(K key, V value, long currentTime) {
                        return durationNanos(value);
                    }

                    @Override
                    public long expireAfterUpdate(
                            K key, V value, long currentTime, long currentDuration) {
                        return durationNanos(value);
                    }

                    @Override
                    public long expireAfterRead(
                            K key, V value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }

                    private long durationNanos(V value) {
                        return (isNegative.test(value) ? negativeTtl : ttl).toNanos();
                    }
                })
                .recordStats()
                .build();
    }

    private static boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }

    private static <T> Optional<T> optional(Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }

    private static String normalizeSymbol(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeCurrency(String value) {
        return value == null || value.isBlank() ? "inr" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeQuery(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
