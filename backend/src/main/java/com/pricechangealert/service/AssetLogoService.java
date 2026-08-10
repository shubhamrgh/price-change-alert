package com.pricechangealert.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricechangealert.model.Market;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Resolves real coin and listed-company logos while keeping third-party details off the frontend. */
@Service
public class AssetLogoService {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private record LogoKey(Market market, String symbol) {
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<LogoKey, Optional<URI>> stockCache = new ConcurrentHashMap<>();
    private final WebClient tradingView = WebClient.builder()
            .baseUrl("https://scanner.tradingview.com")
            .defaultHeader("User-Agent", "Mozilla/5.0")
            .defaultHeader("Content-Type", "application/json")
            .codecs(c -> c.defaultCodecs().maxInMemorySize(1024 * 1024))
            .build();

    public Optional<URI> logoUri(Market market, String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized.isBlank()) return Optional.empty();
        if (market == Market.CRYPTO) {
            if (!normalized.matches("[A-Z0-9]+")) return Optional.empty();
            return Optional.of(URI.create("https://assets.coincap.io/assets/icons/"
                    + normalized.toLowerCase(Locale.ROOT) + "@2x.png"));
        }
        return stockCache.computeIfAbsent(new LogoKey(market, normalized), this::fetchStockLogo);
    }

    private Optional<URI> fetchStockLogo(LogoKey key) {
        String exchange = key.market() == Market.BSE ? "BSE" : "NSE";
        String ticker = exchange + ":" + key.symbol();
        Map<String, Object> payload = Map.of(
                "symbols", Map.of(
                        "tickers", List.of(ticker),
                        "query", Map.of("types", List.of())),
                "columns", List.of("name", "description", "logoid"));
        try {
            String body = tradingView.post()
                    .uri("/india/scan")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .onErrorResume(e -> Mono.empty())
                    .block();
            return parseStockLogo(body, ticker);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    Optional<URI> parseStockLogo(String body, String ticker) {
        try {
            if (body == null || body.isBlank()) return Optional.empty();
            JsonNode data = mapper.readTree(body).path("data");
            if (!data.isArray()) return Optional.empty();
            for (JsonNode result : data) {
                if (!ticker.equalsIgnoreCase(result.path("s").asText(""))) continue;
                String logoId = result.path("d").path(2).asText("");
                if (!logoId.matches("[a-z0-9-]+")) return Optional.empty();
                return Optional.of(URI.create(
                        "https://s3-symbol-logo.tradingview.com/" + logoId + "--big.svg"));
            }
            return Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
