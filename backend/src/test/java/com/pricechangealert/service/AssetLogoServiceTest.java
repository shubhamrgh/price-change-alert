package com.pricechangealert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pricechangealert.cache.ApplicationCaches;
import com.pricechangealert.cache.CacheProperties;
import com.pricechangealert.model.Market;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AssetLogoServiceTest {

    private final AssetLogoService service = new AssetLogoService(
            new ApplicationCaches(new CacheProperties()));

    @Test
    void buildsCoinCapLogoForCryptoSymbol() {
        assertEquals(URI.create("https://assets.coincap.io/assets/icons/btc@2x.png"),
                service.logoUri(Market.CRYPTO, "BTC").orElseThrow());
    }

    @Test
    void parsesTradingViewStockLogoIdentifier() {
        String response = """
                {"data":[{"s":"NSE:PAYTM","d":["PAYTM","One 97 Communications","one-97-communications"]}]}
                """;

        Optional<URI> result = service.parseStockLogo(response, "NSE:PAYTM");

        assertTrue(result.isPresent());
        assertEquals(URI.create(
                "https://s3-symbol-logo.tradingview.com/one-97-communications--big.svg"), result.orElseThrow());
    }
}
