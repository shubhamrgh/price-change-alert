package com.pricechangealert.source;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricechangealert.model.Market;
import com.pricechangealert.model.Quote;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Rate-limited crypto fallback: CoinGecko (official, free, no key).
 * Uses the coins/list endpoint once (cached 24h) so users can type BTC/ETH/SHIB etc.
 *
 * The free tier rate-limits aggressively, so all calls are throttled to one every
 * {@link #MIN_GAP_MS} and every successful quote is cached for {@link #CACHE_TTL_MS}.
 * A cached quote (even slightly stale) is preferred over failing through to a fallback,
 * and the UI shows when the last price was fetched. Both INR and USD are requested in a
 * single /simple/price call, so switching currency is cheap.
 */
@Component
@Order(10)
public class CoinGeckoProvider implements PriceProvider {

    private static final String BASE = "https://api.coingecko.com/api/v3";
    private static final long MIN_GAP_MS = 5_000;
    private static final long CACHE_TTL_MS = 45_000;
    private static final long MAX_STALE_MS = 10 * 60 * 1000;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(6);

    /** Canonical id overrides: the coins/list symbol map can collide (e.g. a meme coin also tickers BTC). */
    private static final Map<String, String> KNOWN_IDS = Map.ofEntries(
            Map.entry("BTC", "bitcoin"),
            Map.entry("ETH", "ethereum"),
            Map.entry("USDT", "tether"),
            Map.entry("BNB", "binancecoin"),
            Map.entry("SOL", "solana"),
            Map.entry("XRP", "ripple"),
            Map.entry("USDC", "usd-coin"),
            Map.entry("ADA", "cardano"),
            Map.entry("DOGE", "dogecoin"),
            Map.entry("MATIC", "matic-network"),
            Map.entry("LTC", "litecoin"),
            Map.entry("DOT", "polkadot"),
            Map.entry("SHIB", "shiba-inu"),
            Map.entry("TRX", "tron"),
            Map.entry("AVAX", "avalanche-2"),
            Map.entry("POL", "polygon-ecosystem-token"),
            Map.entry("LINK", "chainlink"),
            Map.entry("TATASTEEL", "tata-steel-token"));

    private static final String USD = "usd";
    private static final String INR = "inr";

    private record CachedQuote(long fetchedAt, double inr, double usd) {
    }

    private final WebClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Cache<String, CachedQuote> priceCache = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();
    private final Object callGate = new Object();
    private long lastCallAt = 0;

    private volatile Map<String, String> symbolToId = Map.of();
    private volatile List<Coin> coins = List.of();
    private volatile long listFetchedAt = 0;
    private static final long LIST_TTL_MS = 24L * 3600 * 1000;

    /** Lightweight coin metadata for the suggestion picker. */
    public record Coin(String symbol, String id, String name) {
    }

    /** Cached coins (symbol, id, name) for search; refreshes at most once per 24h. */
    public List<Coin> coins() throws Exception {
        loadSymbolMap();
        return coins;
    }

    public CoinGeckoProvider() {
        this.client = WebClient.builder()
                .baseUrl(BASE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
                .build();
    }

    @Override
    public String name() {
        return "coingecko";
    }

    @Override
    public boolean supports(Market market) {
        return market == Market.CRYPTO;
    }

    @Override
    public Optional<Quote> fetch(String symbol, Market market, String currency) {
        if (market != Market.CRYPTO) return Optional.empty();
        String cur = normalize(currency);
        try {
            String upper = symbol.trim().toUpperCase(Locale.ROOT);
            String id = resolveId(upper);
            if (id == null) return Optional.empty();

            CachedQuote cached = updateQuote(id);
            double price = cur.equals(USD) ? cached.usd() : cached.inr();
            if (price <= 0) return Optional.empty();
            return Optional.of(new Quote(Market.CRYPTO, upper,
                    displayName(id), price, cur.toUpperCase(Locale.ROOT), "coingecko",
                    Instant.ofEpochMilli(cached.fetchedAt())));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String displayName(String id) {
        return coins.stream().filter(c -> c.id().equals(id)).map(Coin::name).findFirst()
                .orElse(id);
    }

    /** Return a fresh-enough cached quote for the id, hitting the API at most every MIN_GAP_MS. */
    private CachedQuote updateQuote(String id) {
        CachedQuote cached = priceCache.getIfPresent(id);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetchedAt() < CACHE_TTL_MS) return cached;

        JsonNode node = rateLimitedGet(client.get()
                .uri("/simple/price?ids={id}&vs_currencies=inr,usd", id));
        if (node == null || !node.has(id)) {
            return isUsableStale(cached, now) ? cached : emptyQuote(now);
        }
        JsonNode v = node.path(id);
        double inr = v.path(INR).asDouble(0);
        double usd = v.path(USD).asDouble(0);
        if (inr <= 0 && usd <= 0) return isUsableStale(cached, now) ? cached : emptyQuote(now);
        CachedQuote fresh = new CachedQuote(System.currentTimeMillis(), inr, usd);
        priceCache.put(id, fresh);
        return fresh;
    }

    private static boolean isUsableStale(CachedQuote cached, long now) {
        return cached != null && now - cached.fetchedAt() <= MAX_STALE_MS;
    }

    private static CachedQuote emptyQuote(long now) {
        return new CachedQuote(now - CACHE_TTL_MS, 0, 0);
    }

    private static String normalize(String currency) {
        return currency == null ? INR : currency.trim().toLowerCase(Locale.ROOT);
    }

    /** Explicit canonical id (KNOWN_IDS only, no symbol-map fallback) or empty. */
    public Optional<String> canonicalId(String symbol) {
        return Optional.ofNullable(KNOWN_IDS.get(symbol.trim().toUpperCase(Locale.ROOT)));
    }

    /** Friendly symbol/ID helper used by the chart service too. */
    public String resolveId(String symbol) {
        String upper = symbol.trim().toUpperCase(Locale.ROOT);
        String id = KNOWN_IDS.get(upper);
        if (id != null) return id;
        try {
            loadSymbolMap();
        } catch (Exception ignored) {
        }
        return symbolToId.get(upper);
    }

    /** [[tsMillis, price], ...] history for the chart endpoint. */
    public Optional<List<double[]>> marketChart(String symbol, int days, String currency) {
        String cur = normalize(currency);
        String id = resolveId(symbol);
        if (id == null) return Optional.empty();
        JsonNode node = rateLimitedGet(client.get()
                .uri("/coins/{id}/market_chart?vs_currency={cur}&days={days}", id, cur, days));
        JsonNode prices = node == null ? null : node.path("prices");
        if (prices == null || !prices.isArray() || prices.isEmpty()) return Optional.empty();
        List<double[]> out = new ArrayList<>(prices.size());
        for (JsonNode p : prices) {
            if (p.isArray() && p.size() >= 2) {
                out.add(new double[]{p.get(0).asLong(), p.get(1).asDouble()});
            }
        }
        return out.isEmpty() ? Optional.empty() : Optional.of(out);
    }

    private void loadSymbolMap() throws Exception {
        if (!symbolToId.isEmpty() && System.currentTimeMillis() - listFetchedAt < LIST_TTL_MS) return;
        JsonNode list = rateLimitedGet(client.get().uri("/coins/list"));
        if (list == null || !list.isArray()) return;
        symbolToId = StreamSupport.stream(list.spliterator(), false)
                .filter(n -> n.hasNonNull("symbol") && n.hasNonNull("id") && n.hasNonNull("name"))
                .collect(Collectors.toMap(
                        n -> n.get("symbol").asText().toUpperCase(Locale.ROOT),
                        n -> n.get("id").asText(),
                        (a, b) -> a,
                        ConcurrentHashMap::new));
        coins = StreamSupport.stream(list.spliterator(), false)
                .filter(n -> n.hasNonNull("symbol") && n.hasNonNull("id") && n.hasNonNull("name"))
                .map(n -> new Coin(n.get("symbol").asText().toUpperCase(Locale.ROOT),
                        n.get("id").asText(), n.get("name").asText()))
                .toList();
        listFetchedAt = System.currentTimeMillis();
    }

    private JsonNode getJson(WebClient.RequestHeadersSpec<?> spec) {
        try {
            String body = spec.retrieve().bodyToMono(String.class)
                    .timeout(HTTP_TIMEOUT)
                    .onErrorResume(e -> Mono.empty())
                    .block();
            return body == null || body.isBlank() ? null : mapper.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode rateLimitedGet(WebClient.RequestHeadersSpec<?> request) {
        synchronized (callGate) {
            long wait = MIN_GAP_MS - (System.currentTimeMillis() - lastCallAt);
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            try {
                return getJson(request);
            } finally {
                lastCallAt = System.currentTimeMillis();
            }
        }
    }
}
