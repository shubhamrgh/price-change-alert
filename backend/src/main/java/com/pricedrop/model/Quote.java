package com.pricedrop.model;

import java.time.Instant;

/** A single fetched price quote from one provider. */
public record Quote(Market market, String symbol, String displayName, double price, String currency, String source, Instant fetchedAt) {
}