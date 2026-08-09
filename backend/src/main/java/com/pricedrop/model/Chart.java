package com.pricedrop.model;

import java.util.List;

/** Price history series for the chart: points are [epochMillis, price]. */
public record Chart(List<double[]> points, String source, String currency) {

    public double firstPrice() {
        return points.isEmpty() ? 0 : points.get(0)[1];
    }

    public double lastPrice() {
        return points.isEmpty() ? 0 : points.get(points.size() - 1)[1];
    }
}