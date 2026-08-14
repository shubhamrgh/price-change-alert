package com.trailify.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trailify.cache.ApplicationCaches;
import com.trailify.cache.CacheProperties;
import com.trailify.model.Market;
import com.trailify.model.Suggestion;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchServiceTest {

    private final SearchService service = new SearchService(
            new ApplicationCaches(new CacheProperties()));

    @Test
    void singleLetterStockSearchReturnsImmediateSuggestions() {
        List<Suggestion> results = service.search("a", Market.NSE);

        assertEquals(10, results.size());
        assertTrue(results.stream().allMatch(result -> result.market() == Market.NSE));
        assertTrue(results.stream().allMatch(result -> matches(result, "a")));
    }

    @Test
    void singleLetterCryptoSearchReturnsImmediateSuggestions() {
        List<Suggestion> results = service.search("a", Market.CRYPTO);

        assertEquals(10, results.size());
        assertTrue(results.stream().allMatch(result -> result.market() == Market.CRYPTO));
        assertTrue(results.stream().allMatch(result -> matches(result, "a")));
    }

    @Test
    void ranksMatchesByDescendingRelevance() {
        List<Suggestion> ranked = SearchService.rankSuggestions(List.of(
                new Suggestion("BAR", "Alpha Holdings", Market.NSE),
                new Suggestion("AXIS", "Axis Bank", Market.NSE),
                new Suggestion("A", "Example Industries", Market.NSE),
                new Suggestion("ZZ", "Beta Alpha", Market.NSE)), "a", Market.NSE);

        assertFalse(ranked.isEmpty());
        assertEquals(List.of("A", "AXIS", "BAR", "ZZ"),
                ranked.stream().map(Suggestion::symbol).toList());
    }

    @Test
    void matchesWholeCompanyNameWithLegalSuffix() {
        List<Suggestion> reliance = service.search("Reliance Industries Limited", Market.NSE);
        List<Suggestion> tcs = service.search("Tata Consultancy Services Ltd", Market.NSE);

        assertEquals("RELIANCE", reliance.getFirst().symbol());
        assertEquals("TCS", tcs.getFirst().symbol());
    }

    @Test
    void matchesAliasesForRenamedAndLessCommonStocks() {
        assertEquals("ETERNAL", service.search("Zomato Limited", Market.NSE).getFirst().symbol());
        assertEquals("IRFC", service.search("Indian Railway Finance Corporation Limited", Market.NSE)
                .getFirst().symbol());
    }

    private static boolean matches(Suggestion suggestion, String query) {
        String normalized = query.toLowerCase();
        return suggestion.symbol().toLowerCase().contains(normalized)
                || suggestion.name().toLowerCase().contains(normalized);
    }
}
