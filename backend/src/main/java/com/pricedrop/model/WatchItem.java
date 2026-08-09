package com.pricedrop.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

@Entity
@Table(name = "watch_items")
public class WatchItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", length = 64)
    private String ownerId = "legacy";

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Market market;

    @Column(nullable = false, columnDefinition = "varchar(10)")
    private String currency = "INR";

    @Column(nullable = false, columnDefinition = "varchar(10)")
    private String direction = "BELOW";

    /** PRICE = alert when price crosses thresholdValue; PERCENT = alert when drop % >= thresholdValue */
    @Column(name = "trigger_type", nullable = false)
    private String triggerType = "PRICE";

    @Column(name = "threshold_value", nullable = false)
    private double thresholdValue;

    private Double lastPrice;

    /** Price from the previous poll tick, used to show the last change % on cards. */
    private Double previousPrice;

    /** Price at which the last alert was sent for this item (dedupe for PRICE alerts). */
    private Double lastAlertedPrice;

    private String lastSource;

    private Instant lastFetchedAt;

    private Instant lastAlertedAt;

    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // ---- helpers ----

    public boolean alertTriggered(double currentPrice, double previousPrice) {
        if (!active) return false;
        boolean rises = "ABOVE".equalsIgnoreCase(getDirection());
        if ("PERCENT".equals(triggerType)) {
            if (previousPrice <= 0) return false;
            double movePct = (currentPrice - previousPrice) / previousPrice * 100.0;
            return rises ? movePct >= thresholdValue : -movePct >= thresholdValue;
        }
        // PRICE alert: breach below (drop) or above (rise) the threshold.
        return rises
                ? currentPrice >= thresholdValue
                : currentPrice <= thresholdValue;
    }

    // ---- getters/setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    @JsonIgnore
    public String getOwnerId() { return ownerId == null ? "legacy" : ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Market getMarket() { return market; }
    public void setMarket(Market market) { this.market = market; }
    public String getCurrency() {
        return currency == null ? "INR" : currency;
    }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getDirection() {
        return direction == null ? "BELOW" : direction;
    }
    public void setDirection(String direction) { this.direction = direction; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public double getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(double thresholdValue) { this.thresholdValue = thresholdValue; }
    public Double getLastPrice() { return lastPrice; }
    public void setLastPrice(Double lastPrice) { this.lastPrice = lastPrice; }
    public Double getPreviousPrice() { return previousPrice; }
    public void setPreviousPrice(Double previousPrice) { this.previousPrice = previousPrice; }
    public Double getLastAlertedPrice() { return lastAlertedPrice; }
    public void setLastAlertedPrice(Double lastAlertedPrice) { this.lastAlertedPrice = lastAlertedPrice; }
    public String getLastSource() { return lastSource; }
    public void setLastSource(String lastSource) { this.lastSource = lastSource; }
    public Instant getLastFetchedAt() { return lastFetchedAt; }
    public void setLastFetchedAt(Instant lastFetchedAt) { this.lastFetchedAt = lastFetchedAt; }
    public Instant getLastAlertedAt() { return lastAlertedAt; }
    public void setLastAlertedAt(Instant lastAlertedAt) { this.lastAlertedAt = lastAlertedAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
