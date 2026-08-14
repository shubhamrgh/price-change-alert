package com.trailify.web;

import com.trailify.model.Chart;
import com.trailify.model.Market;
import com.trailify.service.ChartService;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<Chart> chart(@RequestParam Market market,
                                      @RequestParam String symbol,
                                      @RequestParam(defaultValue = "30") int days,
                                      @RequestParam(defaultValue = "inr") String currency) {
        if (days < 1) days = 1;
        if (days > 365) days = 365;
        Chart chart = chartService.chart(symbol, market, days, currency)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No history for " + symbol + " on " + market));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(chart);
    }
}
