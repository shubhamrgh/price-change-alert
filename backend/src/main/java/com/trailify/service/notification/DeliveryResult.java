package com.trailify.service.notification;

public record DeliveryResult(boolean delivered, boolean retryable, String error) {
    public static DeliveryResult sent() {
        return new DeliveryResult(true, false, null);
    }

    public static DeliveryResult retry(String error) {
        return new DeliveryResult(false, true, error);
    }

    public static DeliveryResult failed(String error) {
        return new DeliveryResult(false, false, error);
    }
}
