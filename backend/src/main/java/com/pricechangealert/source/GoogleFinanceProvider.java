package com.pricechangealert.source;

import com.pricechangealert.model.Market;
import com.pricechangealert.model.Quote;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** HTML fallback for Indian stock prices when Yahoo Finance is unavailable. */
@Component
@Order(30)
public class GoogleFinanceProvider implements PriceProvider {

    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    private static final Map<Market, String> EXCHANGE = Map.of(
            Market.NSE, "NSE",
            Market.BSE, "BOM");
    private static final Pattern QUOTE_PATTERN = Pattern.compile(
            "<div class=\"gO24Ff\">([^<]+)</div>.*?"
                    + "<span jsname=\"Pdsbrc\"[^>]*>\\s*<span>([^<]+)</span>",
            Pattern.DOTALL);

    private final WebClient client = WebClient.builder()
            .defaultHeader("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")
            .defaultHeader("Accept-Language", "en-US,en;q=0.9")
            .codecs(c -> c.defaultCodecs().maxInMemorySize(3 * 1024 * 1024))
            .build();

    @Override
    public String name() {
        return "google-finance";
    }

    @Override
    public boolean supports(Market market) {
        return EXCHANGE.containsKey(market);
    }

    @Override
    public Optional<Quote> fetch(String symbol, Market market, String currency) {
        if (!supports(market)) return Optional.empty();
        String sym = symbol.trim().toUpperCase(Locale.ROOT);
        try {
            String encodedSymbol = URLEncoder.encode(sym, StandardCharsets.UTF_8).replace("+", "%20");
            URI uri = URI.create("https://www.google.com/finance/quote/" + encodedSymbol + ":"
                    + EXCHANGE.get(market) + "?hl=en");
            String body = client.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .onErrorResume(e -> Mono.empty())
                    .block();
            return parseQuote(body, sym, market);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    Optional<Quote> parseQuote(String body, String symbol, Market market) {
        if (body == null || body.isBlank()) return Optional.empty();
        Matcher matcher = QUOTE_PATTERN.matcher(body);
        if (!matcher.find()) return Optional.empty();

        String displayName = decodeHtml(matcher.group(1).trim());
        String numericPrice = matcher.group(2).replaceAll("[^0-9.\\-]", "");
        try {
            double price = Double.parseDouble(numericPrice);
            if (price <= 0) return Optional.empty();
            return Optional.of(new Quote(market, symbol, displayName, price, "INR", name(), Instant.now()));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static String decodeHtml(String value) {
        return value.replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace("&quot;", "\"");
    }
}
