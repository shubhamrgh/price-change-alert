package com.pricedrop.web;

import com.pricedrop.model.Market;
import com.pricedrop.model.Quote;
import com.pricedrop.source.PriceService;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Live price lookup, used by the "add symbol" preview. */
@RestController
@RequestMapping("/api/quote")
public class QuoteController {

    private final PriceService priceService;

    public QuoteController(PriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping
    public Quote quote(@RequestParam Market market,
                       @RequestParam String symbol,
                       @RequestParam(defaultValue = "inr") String currency) {
        return priceService.fetch(symbol, market, currency).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "No quote found for " + symbol + " on " + market));
    }
}