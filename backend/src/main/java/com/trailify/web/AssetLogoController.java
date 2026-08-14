package com.trailify.web;

import com.trailify.model.Market;
import com.trailify.service.AssetLogoService;
import java.net.URI;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logo")
public class AssetLogoController {

    private final AssetLogoService logoService;

    public AssetLogoController(AssetLogoService logoService) {
        this.logoService = logoService;
    }

    @GetMapping
    public ResponseEntity<Void> logo(@RequestParam Market market, @RequestParam String symbol) {
        return logoService.logoUri(market, symbol)
                .map(this::redirect)
                .orElseGet(() -> ResponseEntity.notFound()
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                        .build());
    }

    private ResponseEntity<Void> redirect(URI logo) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(logo)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .build();
    }
}
