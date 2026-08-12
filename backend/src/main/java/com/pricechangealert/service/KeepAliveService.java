package com.pricechangealert.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Sends an inbound request through Render's public edge before the free-tier
 * idle timeout. The external GitHub workflow remains the wake-up fallback.
 */
@Service
@ConditionalOnProperty(prefix = "price-change-alert.keep-alive", name = "enabled", havingValue = "true")
public class KeepAliveService {

    private static final Logger log = LoggerFactory.getLogger(KeepAliveService.class);

    private final HttpClient httpClient;
    private final URI livenessUri;

    public KeepAliveService(@Value("${price-change-alert.keep-alive.url}") String livenessUrl) {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), URI.create(livenessUrl));
    }

    KeepAliveService(HttpClient httpClient, URI livenessUri) {
        this.httpClient = httpClient;
        this.livenessUri = livenessUri;
    }

    @Scheduled(
            initialDelayString = "${price-change-alert.keep-alive.initial-delay-ms:120000}",
            fixedDelayString = "${price-change-alert.keep-alive.interval-ms:480000}",
            scheduler = "keepAliveTaskScheduler")
    public void ping() {
        HttpRequest request = HttpRequest.newBuilder(livenessUri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "PriceChangeAlert-KeepAlive/1.0")
                .header("X-Price-Change-Alert-Keep-Alive", "true")
                .GET()
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 400) {
                log.info("Keep-alive request succeeded with HTTP {}", response.statusCode());
            } else {
                log.warn("Keep-alive request returned HTTP {}", response.statusCode());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Keep-alive request was interrupted");
        } catch (IOException | RuntimeException exception) {
            log.warn("Keep-alive request failed: {}", exception.getMessage());
        }
    }
}
