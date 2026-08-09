package com.pricedrop.web;

import java.util.regex.Pattern;

final class VisitorId {

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_-]{6,64}");

    private VisitorId() {
    }

    static String normalize(String value) {
        if (value == null || value.isBlank()) return "legacy";
        String id = value.trim();
        if (!SAFE.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid visitor identifier");
        }
        return id;
    }
}
