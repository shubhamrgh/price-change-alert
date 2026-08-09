package com.pricedrop.web;

import com.pricedrop.model.Chart;
import com.pricedrop.model.Market;
import com.pricedrop.service.ChartService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Price history for charts. */
@RestController
@RequestMapping("/api/chart")
public class ChartController {

    private final ChartService chartService;

    public ChartController(ChartService chartService) {
        this.chartService = chartService;
    }

    @GetMapping
    public Chart chart(@RequestParam Market market,
                       @RequestParam String symbol,
                       @RequestParam(defaultValue = "30") int days,
                       @RequestParam(defaultValue = "inr") String currency) {
        if (days < 1) days = 1;
        if (days > 365) days = 365;
        return chartService.chart(symbol, market, days, currency)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No history for " + symbol + " on " + market));
    }
}