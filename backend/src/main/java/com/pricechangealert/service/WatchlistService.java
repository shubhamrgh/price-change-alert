package com.pricechangealert.service;

import com.pricechangealert.model.Market;
import com.pricechangealert.model.WatchItem;
import com.pricechangealert.repository.WatchItemRepository;
import com.pricechangealert.source.PriceService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WatchlistService {

    private final WatchItemRepository repository;
    private final PriceService priceService;

    public WatchlistService(WatchItemRepository repository, PriceService priceService) {
        this.repository = repository;
        this.priceService = priceService;
    }

    public List<WatchItem> findAll(String ownerId) {
        return repository.findAllOwnedBy(ownerId);
    }

    public Optional<WatchItem> findById(Long id, String ownerId) {
        return repository.findOwnedById(id, ownerId);
    }

    @Transactional
    public WatchItem add(String ownerId, String symbol, String name, Market market, String triggerType,
                         String direction, double thresholdValue, String currency) {
        String sym = symbol.trim().toUpperCase();
        String cur = currency == null || currency.isBlank() ? "INR" : currency.trim().toUpperCase();
        if (!"INR".equals(cur) && !"USD".equals(cur)) cur = "INR";
        String dir = direction == null || direction.isBlank() ? "BELOW" : direction.trim().toUpperCase();
        if (!"BELOW".equals(dir) && !"ABOVE".equals(dir)) dir = "BELOW";
        if (repository.existsByOwnerIdAndSymbolAndMarket(ownerId, sym, market)) {
            throw new IllegalArgumentException("Already watching " + sym + " on " + market);
        }
        if (!"PRICE".equals(triggerType) && !"PERCENT".equals(triggerType)) {
            throw new IllegalArgumentException("triggerType must be PRICE or PERCENT");
        }
        WatchItem item = new WatchItem();
        item.setOwnerId(ownerId);
        item.setSymbol(sym);
        item.setName(name == null || name.isBlank() ? sym : name.trim());
        item.setMarket(market);
        item.setTriggerType(triggerType);
        item.setDirection(dir); // BELOW = drop, ABOVE = rise; applies to PRICE and PERCENT
        item.setThresholdValue(thresholdValue);
        item.setCurrency(cur);

        // prime the first price so alerts only fire on real drops going forward.
        // Stocks are only quoted in INR by the sources, so fall back to the real
        // quote currency instead of storing a misleading "USD" figure.
        priceService.fetch(sym, market, cur).ifPresent(q -> {
            item.setCurrency(q.currency().toUpperCase());
            item.setLastPrice(q.price());
            item.setLastSource(q.source());
            item.setLastFetchedAt(q.fetchedAt());
        });

        return repository.save(item);
    }

    @Transactional
    public void delete(Long id, String ownerId) {
        repository.findOwnedById(id, ownerId).ifPresent(repository::delete);
    }

    @Transactional
    public WatchItem setActive(Long id, String ownerId, boolean active) {
        WatchItem item = repository.findOwnedById(id, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Watch item not found: " + id));
        item.setActive(active);
        return repository.save(item);
    }
}

