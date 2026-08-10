package com.pricechangealert.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricechangealert.model.Market;
import com.pricechangealert.model.Quote;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Universal fallback: Yahoo Finance v8 chart API (unofficial, no key needed).
 * NSE -> SYMBOL.NS, BSE -> SYMBOL.BO, crypto -> SYMBOL-INR.
 */
@Component
@Order(20)
public class YahooProvider implements PriceProvider {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final List<String> HOSTS = List.of(
            "https://query1.finance.yahoo.com",
            "https://query2.finance.yahoo.com");

    private static final Map<Market, String> SUFFIX = Map.of(
            Market.NSE, ".NS",
            Market.BSE, ".BO");

    private final ObjectMapper mapper = new ObjectMapper();
    private final WebClient client;

    public YahooProvider() {
        this.client = WebClient.builder()
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .defaultHeader("Accept", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    @Override
    public String name() {
        return "yahoo";
    }

    @Override
    public boolean supports(Market market) {
        return market == Market.CRYPTO || SUFFIX.containsKey(market);
    }

    @Override
    public Optional<Quote> fetch(String symbol, Market market, String currency) {
        if (!supports(market)) return Optional.empty();
        try {
            String sym = symbol.trim().toUpperCase(Locale.ROOT);
            String ticker = tickerFor(sym, market, currency);
            for (String host : HOSTS) {
                JsonNode node = getJson(client.get().uri(
                        host + "/v8/finance/chart/{ticker}?interval=1d&range=1d", ticker));
                Optional<Quote> quote = parseQuote(node, sym, market);
                if (quote.isPresent()) return quote;
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<Quote> parseQuote(JsonNode node, String symbol, Market market) {
        if (node == null || !node.path("chart").path("error").isNull()) return Optional.empty();
        JsonNode meta = node.path("chart").path("result").path(0).path("meta");
        double price = meta.path("regularMarketPrice").asDouble(0);
        if (price <= 0) return Optional.empty();
        String currencyOut = meta.path("currency").asText(market == Market.CRYPTO ? "USD" : "INR");
        String displayName = meta.path("shortName").asText("");
        if (displayName.isBlank()) displayName = meta.path("longName").asText(symbol);
        return Optional.of(new Quote(market, symbol, displayName, price, currencyOut, name(), Instant.now()));
    }

    private String tickerFor(String sym, Market market, String currency) {
        if (market != Market.CRYPTO) return sym + SUFFIX.get(market);
        return "usd".equalsIgnoreCase(currency) ? sym + "-USD" : sym + "-INR";
    }

    /** [[tsMillis, closePrice], ...] daily history for the chart endpoint; stocks use the Yahoo fallback. */
    public Optional<List<double[]>> history(String symbol, Market market, int days, String currency) {
        if (!supports(market)) return Optional.empty();
        try {
            String sym = symbol.trim().toUpperCase(Locale.ROOT);
            String ticker = tickerFor(sym, market, currency);
            String range = days <= 8 ? "5d" : days <= 31 ? "1mo" : days <= 95 ? "3mo" : days <= 190 ? "6mo" : "1y";
            JsonNode node = null;
            for (String host : HOSTS) {
                node = getJson(client.get().uri(
                        host + "/v8/finance/chart/{ticker}?interval=1d&range={range}", ticker, range));
                if (node != null && node.path("chart").path("error").isNull()) break;
            }
            if (node == null || !node.path("chart").path("error").isNull()) return Optional.empty();
            JsonNode timestamps = node.path("chart").path("result").path(0).path("timestamp");
            JsonNode closes = node.path("chart").path("result").path(0).path("indicators").path("quote").path(0).path("close");
            if (!timestamps.isArray() || !closes.isArray() || timestamps.isEmpty()) return Optional.empty();
            List<double[]> out = new ArrayList<>(Math.min(timestamps.size(), closes.size()));
            for (int i = 0; i < timestamps.size() && i < closes.size(); i++) {
                double price = closes.get(i).asDouble(0);
                if (price > 0) out.add(new double[]{timestamps.get(i).asLong() * 1000L, price});
            }
            return out.isEmpty() ? Optional.empty() : Optional.of(out);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private JsonNode getJson(WebClient.RequestHeadersSpec<?> spec) {
        try {
            String body = spec.retrieve().bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .onErrorResume(e -> Mono.empty())
                    .block();
            return body == null || body.isBlank() ? null : mapper.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }
}
