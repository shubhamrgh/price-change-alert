package com.pricechangealert.web;

import com.pricechangealert.model.Market;
import com.pricechangealert.model.Quote;
import com.pricechangealert.source.PriceService;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Live price lookup, used by the "add symbol" preview. */
@RestController
@RequestMapping("/api/quote")
public class QuoteController {

    private final PriceService priceService;

    public QuoteController(PriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping
    public ResponseEntity<Quote> quote(@RequestParam Market market,
                                      @RequestParam String symbol,
                                      @RequestParam(defaultValue = "inr") String currency) {
        Quote quote = priceService.fetch(symbol, market, currency)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No quote found for " + symbol + " on " + market));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(20)).cachePublic())
                .body(quote);
    }
}
