package com.trailify.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trailify.model.Market;
import com.trailify.model.Quote;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GoogleFinanceProviderTest {

    @Test
    void parsesIndianStockPriceFromQuotePage() {
        GoogleFinanceProvider provider = new GoogleFinanceProvider();
        String html = """
                <div class="gO24Ff">Reliance Industries Ltd</div>
                <div><span jsname="Pdsbrc" class=""><span>₹1,324.00</span></span></div>
                """;

        Optional<Quote> result = provider.parseQuote(html, "RELIANCE", Market.NSE);

        assertTrue(result.isPresent());
        assertEquals(1_324.00, result.orElseThrow().price());
        assertEquals("Reliance Industries Ltd", result.orElseThrow().displayName());
        assertEquals("google-finance", result.orElseThrow().source());
    }
}
