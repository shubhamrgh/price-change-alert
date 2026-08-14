package com.trailify.cache;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.stereotype.Component;

/** Registers cache hit, miss, eviction, and size metrics with Spring Boot Actuator. */
@Component
class ApplicationCacheMetrics {

    ApplicationCacheMetrics(MeterRegistry registry, ApplicationCaches caches) {
        CaffeineCacheMetrics.monitor(registry, caches.quoteCache(), "quotes");
        CaffeineCacheMetrics.monitor(registry, caches.searchCache(), "searches");
        CaffeineCacheMetrics.monitor(registry, caches.chartCache(), "charts");
        CaffeineCacheMetrics.monitor(registry, caches.logoCache(), "logos");
    }
}
