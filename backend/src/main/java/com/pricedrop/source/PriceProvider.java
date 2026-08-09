package com.pricedrop.source;

import com.pricedrop.model.Market;
import com.pricedrop.model.Quote;
import java.util.Optional;

/** A data provider able to quote a given market. Multiple providers chain as fallbacks. */
public interface PriceProvider {

    /** Unique provider name, e.g. "coingecko", "nse", "bse", "yahoo". */
    String name();

    /** True if this provider can possibly serve the market (primary providers return true with priority order). */
    boolean supports(Market market);

    /**
     * Fetch a live quote. Empty if symbol unknown or provider failed.
     * @param currency requested currency ("inr" or "usd"); providers may return their
     *                 native currency if the requested one isn't available.
     */
    Optional<Quote> fetch(String symbol, Market market, String currency);
}