package com.trailify.cache;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Tunable bounds and freshness windows for application-level caches. */
@Component
@Validated
@ConfigurationProperties(prefix = "trailify.cache")
public class CacheProperties {

    @NotNull
    private Duration quoteTtl = Duration.ofSeconds(20);

    @NotNull
    private Duration quoteNegativeTtl = Duration.ofSeconds(5);

    @Positive
    private long quoteMaximumSize = 2_000;

    @NotNull
    private Duration searchTtl = Duration.ofMinutes(10);

    @NotNull
    private Duration searchNegativeTtl = Duration.ofSeconds(30);

    @Positive
    private long searchMaximumSize = 3_000;

    @NotNull
    private Duration chartTtl = Duration.ofMinutes(5);

    @NotNull
    private Duration chartNegativeTtl = Duration.ofSeconds(30);

    @Positive
    private long chartMaximumSize = 1_000;

    @NotNull
    private Duration logoTtl = Duration.ofHours(24);

    @NotNull
    private Duration logoNegativeTtl = Duration.ofHours(1);

    @Positive
    private long logoMaximumSize = 2_000;

    public Duration getQuoteTtl() {
        return quoteTtl;
    }

    public void setQuoteTtl(Duration quoteTtl) {
        this.quoteTtl = quoteTtl;
    }

    public Duration getQuoteNegativeTtl() {
        return quoteNegativeTtl;
    }

    public void setQuoteNegativeTtl(Duration quoteNegativeTtl) {
        this.quoteNegativeTtl = quoteNegativeTtl;
    }

    public long getQuoteMaximumSize() {
        return quoteMaximumSize;
    }

    public void setQuoteMaximumSize(long quoteMaximumSize) {
        this.quoteMaximumSize = quoteMaximumSize;
    }

    public Duration getSearchTtl() {
        return searchTtl;
    }

    public void setSearchTtl(Duration searchTtl) {
        this.searchTtl = searchTtl;
    }

    public Duration getSearchNegativeTtl() {
        return searchNegativeTtl;
    }

    public void setSearchNegativeTtl(Duration searchNegativeTtl) {
        this.searchNegativeTtl = searchNegativeTtl;
    }

    public long getSearchMaximumSize() {
        return searchMaximumSize;
    }

    public void setSearchMaximumSize(long searchMaximumSize) {
        this.searchMaximumSize = searchMaximumSize;
    }

    public Duration getChartTtl() {
        return chartTtl;
    }

    public void setChartTtl(Duration chartTtl) {
        this.chartTtl = chartTtl;
    }

    public Duration getChartNegativeTtl() {
        return chartNegativeTtl;
    }

    public void setChartNegativeTtl(Duration chartNegativeTtl) {
        this.chartNegativeTtl = chartNegativeTtl;
    }

    public long getChartMaximumSize() {
        return chartMaximumSize;
    }

    public void setChartMaximumSize(long chartMaximumSize) {
        this.chartMaximumSize = chartMaximumSize;
    }

    public Duration getLogoTtl() {
        return logoTtl;
    }

    public void setLogoTtl(Duration logoTtl) {
        this.logoTtl = logoTtl;
    }

    public Duration getLogoNegativeTtl() {
        return logoNegativeTtl;
    }

    public void setLogoNegativeTtl(Duration logoNegativeTtl) {
        this.logoNegativeTtl = logoNegativeTtl;
    }

    public long getLogoMaximumSize() {
        return logoMaximumSize;
    }

    public void setLogoMaximumSize(long logoMaximumSize) {
        this.logoMaximumSize = logoMaximumSize;
    }
}
