package com.trailify.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trailify.cache.ApplicationCaches;
import com.trailify.cache.ApplicationCaches.SearchKey;
import com.trailify.model.Market;
import com.trailify.model.Suggestion;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Symbol lookup for the add-symbol picker.
 *
 * <p>A small built-in catalog makes one-letter searches instant and keeps the picker useful when
 * an upstream API is rate-limited. Live Yahoo Finance and CoinGecko search fill gaps outside that
 * catalog, then every result is sorted by descending match relevance.</p>
 */
@Service
public class SearchService {

    private static final int LIMIT = 10;
    private static final Duration REMOTE_TIMEOUT = Duration.ofSeconds(4);
    private static final Set<String> COMPANY_SUFFIXES = Set.of(
            "co", "company", "corp", "corporation", "inc", "incorporated", "ltd", "limited", "plc");

    private record CatalogEntry(String symbol, String name) {
    }

    private record RankedSuggestion(Suggestion suggestion, int score) {
    }

    private static CatalogEntry asset(String symbol, String name) {
        return new CatalogEntry(symbol, name);
    }

    private static final List<CatalogEntry> STOCK_CATALOG = List.of(
            asset("RELIANCE", "Reliance Industries"),
            asset("TCS", "Tata Consultancy Services"),
            asset("HDFCBANK", "HDFC Bank"),
            asset("ICICIBANK", "ICICI Bank"),
            asset("INFY", "Infosys"),
            asset("BHARTIARTL", "Bharti Airtel"),
            asset("SBIN", "State Bank of India"),
            asset("LICI", "Life Insurance Corporation of India"),
            asset("ITC", "ITC"),
            asset("HINDUNILVR", "Hindustan Unilever"),
            asset("LT", "Larsen & Toubro"),
            asset("BAJFINANCE", "Bajaj Finance"),
            asset("MARUTI", "Maruti Suzuki India"),
            asset("HCLTECH", "HCL Technologies"),
            asset("SUNPHARMA", "Sun Pharmaceutical Industries"),
            asset("ADANIENT", "Adani Enterprises"),
            asset("ADANIPORTS", "Adani Ports and Special Economic Zone"),
            asset("ADANIGREEN", "Adani Green Energy"),
            asset("ADANIPOWER", "Adani Power"),
            asset("ADANIENSOL", "Adani Energy Solutions"),
            asset("KOTAKBANK", "Kotak Mahindra Bank"),
            asset("AXISBANK", "Axis Bank"),
            asset("TITAN", "Titan Company"),
            asset("ASIANPAINT", "Asian Paints"),
            asset("ULTRACEMCO", "UltraTech Cement"),
            asset("NTPC", "NTPC"),
            asset("ONGC", "Oil and Natural Gas Corporation"),
            asset("POWERGRID", "Power Grid Corporation of India"),
            asset("M&M", "Mahindra & Mahindra"),
            asset("TATASTEEL", "Tata Steel"),
            asset("TATAMOTORS", "Tata Motors"),
            asset("COALINDIA", "Coal India"),
            asset("JSWSTEEL", "JSW Steel"),
            asset("NESTLEIND", "Nestle India"),
            asset("WIPRO", "Wipro"),
            asset("TECHM", "Tech Mahindra"),
            asset("INDUSINDBK", "IndusInd Bank"),
            asset("DRREDDY", "Dr. Reddy's Laboratories"),
            asset("CIPLA", "Cipla"),
            asset("APOLLOHOSP", "Apollo Hospitals Enterprise"),
            asset("EICHERMOT", "Eicher Motors"),
            asset("GRASIM", "Grasim Industries"),
            asset("HDFCLIFE", "HDFC Life Insurance"),
            asset("SBILIFE", "SBI Life Insurance"),
            asset("BAJAJFINSV", "Bajaj Finserv"),
            asset("BPCL", "Bharat Petroleum Corporation"),
            asset("HEROMOTOCO", "Hero MotoCorp"),
            asset("BRITANNIA", "Britannia Industries"),
            asset("DIVISLAB", "Divi's Laboratories"),
            asset("HINDALCO", "Hindalco Industries"),
            asset("AUBANK", "AU Small Finance Bank"),
            asset("AUROPHARMA", "Aurobindo Pharma"),
            asset("ASHOKLEY", "Ashok Leyland"),
            asset("ABCAPITAL", "Aditya Birla Capital"),
            asset("ACC", "ACC"),
            asset("APLAPOLLO", "APL Apollo Tubes"),
            asset("ASTRAL", "Astral"),
            asset("ETERNAL", "Eternal (formerly Zomato)"),
            asset("PAYTM", "One 97 Communications"),
            asset("IRFC", "Indian Railway Finance Corporation"),
            asset("PERSISTENT", "Persistent Systems"),
            asset("TRENT", "Trent"),
            asset("BEL", "Bharat Electronics"),
            asset("HAL", "Hindustan Aeronautics"),
            asset("IRCTC", "Indian Railway Catering and Tourism Corporation"),
            asset("RVNL", "Rail Vikas Nigam"),
            asset("TATAPOWER", "Tata Power"),
            asset("JIOFIN", "Jio Financial Services"),
            asset("IREDA", "Indian Renewable Energy Development Agency"),
            asset("DIXON", "Dixon Technologies"),
            asset("VBL", "Varun Beverages"),
            asset("DMART", "Avenue Supermarts"),
            asset("MOTHERSON", "Samvardhana Motherson International"),
            asset("CANBK", "Canara Bank"),
            asset("IDFCFIRSTB", "IDFC First Bank"),
            asset("FEDERALBNK", "The Federal Bank"),
            asset("YESBANK", "Yes Bank"),
            asset("BANKBARODA", "Bank of Baroda"),
            asset("PNB", "Punjab National Bank"),
            asset("IOC", "Indian Oil Corporation"),
            asset("HINDPETRO", "Hindustan Petroleum"),
            asset("TATACONSUM", "Tata Consumer Products"),
            asset("TATACOMM", "Tata Communications"),
            asset("TATAELXSI", "Tata Elxsi"),
            asset("LTIM", "LTIMindtree"),
            asset("COFORGE", "Coforge"),
            asset("MUTHOOTFIN", "Muthoot Finance"),
            asset("MANAPPURAM", "Manappuram Finance"),
            asset("POLYCAB", "Polycab India"),
            asset("MCX", "Multi Commodity Exchange of India"),
            asset("NYKAA", "FSN E-Commerce Ventures"),
            asset("DELHIVERY", "Delhivery"));

    private static final List<CatalogEntry> CRYPTO_CATALOG = List.of(
            asset("BTC", "Bitcoin"),
            asset("ETH", "Ethereum"),
            asset("USDT", "Tether"),
            asset("BNB", "BNB"),
            asset("SOL", "Solana"),
            asset("USDC", "USD Coin"),
            asset("XRP", "XRP"),
            asset("DOGE", "Dogecoin"),
            asset("ADA", "Cardano"),
            asset("AVAX", "Avalanche"),
            asset("SHIB", "Shiba Inu"),
            asset("DOT", "Polkadot"),
            asset("LINK", "Chainlink"),
            asset("POL", "Polygon Ecosystem Token"),
            asset("MATIC", "Polygon"),
            asset("LTC", "Litecoin"),
            asset("BCH", "Bitcoin Cash"),
            asset("UNI", "Uniswap"),
            asset("ATOM", "Cosmos"),
            asset("XLM", "Stellar"),
            asset("ETC", "Ethereum Classic"),
            asset("NEAR", "NEAR Protocol"),
            asset("APT", "Aptos"),
            asset("FIL", "Filecoin"),
            asset("ICP", "Internet Computer"),
            asset("HBAR", "Hedera"),
            asset("ARB", "Arbitrum"),
            asset("OP", "Optimism"),
            asset("AAVE", "Aave"),
            asset("ALGO", "Algorand"),
            asset("PEPE", "Pepe"),
            asset("TRX", "TRON"),
            asset("TON", "Toncoin"),
            asset("SUI", "Sui"),
            asset("TAO", "Bittensor"),
            asset("AR", "Arweave"),
            asset("AXS", "Axie Infinity"),
            asset("AKT", "Akash Network"),
            asset("AERO", "Aerodrome Finance"),
            asset("ANKR", "Ankr"),
            asset("AMP", "Amp"));

    private final ObjectMapper mapper = new ObjectMapper();
    private final WebClient yahoo;
    private final WebClient coinGecko;
    private final ApplicationCaches caches;

    @Autowired
    public SearchService(ApplicationCaches caches) {
        this(WebClient.builder()
                        .defaultHeader("User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                        .build(),
                WebClient.builder()
                        .baseUrl("https://api.coingecko.com/api/v3")
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                        .build(),
                caches);
    }

    SearchService(WebClient yahoo, WebClient coinGecko, ApplicationCaches caches) {
        this.yahoo = yahoo;
        this.coinGecko = coinGecko;
        this.caches = caches;
    }

    public List<Suggestion> search(String q, Market market) {
        String query = q == null ? "" : q.trim();
        if (query.isEmpty() || market == null) return List.of();
        SearchKey key = new SearchKey(market, query);
        return caches.search(key, () -> searchUncached(key.query(), key.market()));
    }

    private List<Suggestion> searchUncached(String query, Market market) {
        return switch (market) {
            case CRYPTO -> searchCrypto(query);
            case NSE -> searchStocks(query, Market.NSE, ".NS");
            case BSE -> searchStocks(query, Market.BSE, ".BO");
        };
    }

    private List<Suggestion> searchCrypto(String query) {
        List<Suggestion> local = catalogSuggestions(CRYPTO_CATALOG, Market.CRYPTO);
        List<Suggestion> localMatches = rankSuggestions(local, query, Market.CRYPTO);
        if (!localMatches.isEmpty()) return localMatches;

        List<Suggestion> candidates = new ArrayList<>(fetchCryptoSuggestions(query));
        candidates.addAll(local);
        return rankSuggestions(candidates, query, Market.CRYPTO);
    }

    private List<Suggestion> searchStocks(String query, Market market, String suffix) {
        List<Suggestion> local = catalogSuggestions(STOCK_CATALOG, market);
        List<Suggestion> localMatches = rankSuggestions(local, query, market);
        if (!localMatches.isEmpty()) return localMatches;

        List<Suggestion> candidates = new ArrayList<>(fetchYahooSuggestions(query, market, suffix));
        candidates.addAll(local);
        return rankSuggestions(candidates, query, market);
    }

    private List<Suggestion> fetchCryptoSuggestions(String query) {
        try {
            String body = fetchBody(coinGecko.get().uri(uriBuilder -> uriBuilder
                    .path("/search")
                    .queryParam("query", query)
                    .build()));
            if (body == null) return List.of();

            JsonNode coins = mapper.readTree(body).path("coins");
            if (!coins.isArray()) return List.of();

            List<Suggestion> out = new ArrayList<>();
            for (JsonNode coin : coins) {
                String symbol = coin.path("symbol").asText("").trim().toUpperCase(Locale.ROOT);
                String name = coin.path("name").asText("").trim();
                if (symbol.isBlank() || name.isBlank()) continue;
                out.add(new Suggestion(symbol, name, Market.CRYPTO));
                if (out.size() >= LIMIT * 2) break;
            }
            return out;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<Suggestion> fetchYahooSuggestions(String query, Market market, String suffix) {
        try {
            String body = fetchBody(yahoo.get().uri(uriBuilder -> uriBuilder
                    .scheme("https")
                    .host("query1.finance.yahoo.com")
                    .path("/v1/finance/search")
                    .queryParam("q", query)
                    .queryParam("quotesCount", LIMIT * 2)
                    .queryParam("newsCount", 0)
                    .build()));
            if (body == null) return List.of();

            JsonNode quotes = mapper.readTree(body).path("quotes");
            if (!quotes.isArray()) return List.of();

            List<Suggestion> out = new ArrayList<>();
            for (JsonNode quote : quotes) {
                String symbol = quote.path("symbol").asText("").trim();
                if (!symbol.toUpperCase(Locale.ROOT).endsWith(suffix)) continue;

                String symbolBase = symbol.substring(0, symbol.length() - suffix.length())
                        .toUpperCase(Locale.ROOT);
                String name = quote.path("shortname").asText("").trim();
                if (name.isBlank()) name = quote.path("longname").asText("").trim();
                if (name.isBlank()) name = symbolBase;
                out.add(new Suggestion(symbolBase, name, market));
                if (out.size() >= LIMIT * 2) break;
            }
            return out;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String fetchBody(WebClient.RequestHeadersSpec<?> request) {
        return request.retrieve()
                .bodyToMono(String.class)
                .timeout(REMOTE_TIMEOUT)
                .onErrorResume(e -> Mono.empty())
                .block();
    }

    private static List<Suggestion> catalogSuggestions(List<CatalogEntry> catalog, Market market) {
        return catalog.stream()
                .map(entry -> new Suggestion(entry.symbol(), entry.name(), market))
                .toList();
    }

    static List<Suggestion> rankSuggestions(List<Suggestion> candidates, String query, Market market) {
        String normalizedQuery = normalize(query);
        Map<String, Suggestion> unique = new LinkedHashMap<>();
        for (Suggestion candidate : candidates) {
            if (candidate == null || candidate.market() != market) continue;
            String symbol = candidate.symbol() == null ? "" : candidate.symbol().trim().toUpperCase(Locale.ROOT);
            String name = candidate.name() == null ? symbol : candidate.name().trim();
            if (symbol.isBlank()) continue;
            unique.putIfAbsent(symbol, new Suggestion(symbol, name.isBlank() ? symbol : name, market));
        }

        Comparator<RankedSuggestion> byRelevance = Comparator
                .comparingInt(RankedSuggestion::score).reversed()
                .thenComparing(r -> r.suggestion().name(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(r -> r.suggestion().symbol(), String.CASE_INSENSITIVE_ORDER);

        return unique.values().stream()
                .map(suggestion -> new RankedSuggestion(suggestion, matchScore(suggestion, normalizedQuery)))
                .filter(ranked -> ranked.score() >= 0)
                .sorted(byRelevance)
                .map(RankedSuggestion::suggestion)
                .limit(LIMIT)
                .toList();
    }

    private static int matchScore(Suggestion suggestion, String query) {
        String symbol = normalize(suggestion.symbol());
        String name = normalize(suggestion.name());
        String comparableQuery = comparableCompanyName(query);
        String comparableName = comparableCompanyName(name);
        if (symbol.equals(query)) return 1_000;
        if (name.equals(query)) return 950;
        if (!comparableQuery.isBlank() && comparableName.equals(comparableQuery)) return 940;
        if (symbol.startsWith(query)) return 900;
        if (name.startsWith(query)) return 850;
        if (!comparableQuery.isBlank() && (comparableName.startsWith(comparableQuery)
                || comparableQuery.startsWith(comparableName))) return 825;
        if (wordStartsWith(name, query)) return 800;
        if (allWordsMatch(comparableName, comparableQuery)) return 750;
        if (symbol.contains(query)) return 700;
        if (name.contains(query)) return 650;
        return -1;
    }

    private static boolean allWordsMatch(String name, String query) {
        if (name.isBlank() || query.isBlank()) return false;
        for (String word : query.split(" ")) {
            if (!name.contains(word)) return false;
        }
        return true;
    }

    private static boolean wordStartsWith(String value, String query) {
        for (String word : value.split("[\\s\\-./&]+")) {
            if (word.startsWith(query)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String comparableCompanyName(String value) {
        String normalized = normalize(value).replaceAll("[^a-z0-9]+", " ");
        return java.util.Arrays.stream(normalized.split("\\s+"))
                .filter(word -> !word.isBlank() && !COMPANY_SUFFIXES.contains(word))
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }
}
