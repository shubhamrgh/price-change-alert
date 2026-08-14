package com.trailify.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trailify.model.Market;
import com.trailify.model.Quote;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Fast no-key crypto spot-price fallback. */
@Component
@Order(5)
public class CoinbaseProvider implements PriceProvider {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper mapper = new ObjectMapper();
    private final WebClient client = WebClient.builder()
            .baseUrl("https://api.coinbase.com")
            .defaultHeader("Accept", "application/json")
            .defaultHeader("User-Agent", "Trailify/1.0")
            .build();

    @Override
    public String name() {
        return "coinbase";
    }

    @Override
    public boolean supports(Market market) {
        return market == Market.CRYPTO;
    }

    @Override
    public Optional<Quote> fetch(String symbol, Market market, String currency) {
        if (market != Market.CRYPTO) return Optional.empty();
        String sym = symbol.trim().toUpperCase(Locale.ROOT);
        String cur = "usd".equalsIgnoreCase(currency) ? "USD" : "INR";
        try {
            String body = client.get()
                    .uri("/v2/prices/{pair}/spot", sym + "-" + cur)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .onErrorResume(e -> Mono.empty())
                    .block();
            return parseQuote(body, sym, cur);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    Optional<Quote> parseQuote(String body, String symbol, String currency) {
        try {
            if (body == null || body.isBlank()) return Optional.empty();
            JsonNode data = mapper.readTree(body).path("data");
            double price = data.path("amount").asDouble(0);
            if (price <= 0) return Optional.empty();
            String base = data.path("base").asText(symbol).toUpperCase(Locale.ROOT);
            String cur = data.path("currency").asText(currency).toUpperCase(Locale.ROOT);
            return Optional.of(new Quote(Market.CRYPTO, base, base, price, cur, name(), Instant.now()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
