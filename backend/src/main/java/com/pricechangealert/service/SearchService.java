package com.pricechangealert.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricechangealert.model.Market;
import com.pricechangealert.model.Suggestion;
import com.pricechangealert.source.CoinGeckoProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Symbol lookup for the add-symbol picker. Real data:
 * crypto from CoinGecko's coins list, stocks from Yahoo's search endpoint (filtered to the chosen exchange).
 */
@Service
public class SearchService {

    private static final int LIMIT = 10;

    private final CoinGeckoProvider coinGecko;
    private final ObjectMapper mapper = new ObjectMapper();
    private final WebClient yahoo;

    public SearchService(CoinGeckoProvider coinGecko) {
        this.coinGecko = coinGecko;
        this.yahoo = WebClient.builder()
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    public List<Suggestion> search(String q, Market market) {
        String query = q == null ? "" : q.trim();
        if (query.isEmpty()) return List.of();
        return switch (market) {
            case CRYPTO -> searchCrypto(query);
            case NSE -> searchStocks(query, ".NS");
            case BSE -> searchStocks(query, ".BO");
        };
    }

    private List<Suggestion> searchCrypto(String q) {
        String lower = q.toLowerCase(Locale.ROOT);
        java.util.Set<String> seen = java.util.HashSet.newHashSet(LIMIT * 2);
        try {
            List<CoinGeckoProvider.Coin> all = coinGecko.coins();
            List<CoinGeckoProvider.Coin> exactName = new ArrayList<>();
            List<CoinGeckoProvider.Coin> exactSym = new ArrayList<>();
            List<CoinGeckoProvider.Coin> prefix = new ArrayList<>();
            List<CoinGeckoProvider.Coin> word = new ArrayList<>();
            for (CoinGeckoProvider.Coin c : all) {
                String sym = c.symbol().toLowerCase(Locale.ROOT);
                String name = c.name().toLowerCase(Locale.ROOT);
                if (name.equals(lower)) exactName.add(c);
                else if (sym.equals(lower)) exactSym.add(c);
                else if (sym.startsWith(lower) || name.startsWith(lower)) prefix.add(c);
                else if (name.contains(" " + lower)) word.add(c);
            }
            List<CoinGeckoProvider.Coin> ranked = new ArrayList<>(exactName);
            ranked.addAll(exactSym);
            ranked.addAll(prefix);
            ranked.addAll(word);
            // canonical known-id match (KNOWN_IDS overrides collisions, e.g. BTC -> bitcoin)
            // is forced to the very front so typing "btc" always lands on Bitcoin.
            Optional<String> knownId = coinGecko.canonicalId(q);
            if (knownId.isPresent()) {
                for (int i = 0; i < ranked.size(); i++) {
                    if (ranked.get(i).id().equals(knownId.orElse(""))) {
                        CoinGeckoProvider.Coin canonical = ranked.remove(i);
                        ranked.add(0, canonical);
                        break;
                    }
                }
            }
            return ranked.stream()
                    .map(c -> new Suggestion(c.symbol(), c.name(), Market.CRYPTO))
                    .filter(s -> seen.add(s.symbol()))
                    .limit(LIMIT)
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Suggestion> searchStocks(String query, String suffix) {
        try {
            String body = yahoo.get()
                    .uri("https://query1.finance.yahoo.com/v1/finance/search?q={q}&quotesCount=20&newsCount=0", query)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> Mono.empty())
                    .block();
            if (body == null) return List.of();
            JsonNode quote = mapper.readTree(body).path("quotes");
            if (!quote.isArray()) return List.of();
            Market market = ".NS".equals(suffix) ? Market.NSE : Market.BSE;
            List<Suggestion> out = new ArrayList<>();
            for (JsonNode n : quote) {
                String symbol = n.path("symbol").asText("");
                if (!symbol.endsWith(suffix)) continue;
                String symbolBase = symbol.substring(0, symbol.length() - 3);
                String name = n.path("shortname").asText(symbolBase);
                if (name.isBlank()) name = n.path("longname").asText(symbolBase);
                out.add(new Suggestion(symbolBase, name, market));
                if (out.size() >= LIMIT) break;
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
