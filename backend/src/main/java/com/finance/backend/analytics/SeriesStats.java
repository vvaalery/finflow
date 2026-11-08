package com.finance.backend.analytics;

import java.util.List;

public record SeriesStats(
        int months, double mean, double median, double std, double cv,
        double min, double max,
        double slopePerMonth, double slopePercent, double r2, String trend,
        double lag1Autocorrelation, double seasonalStrength,
        double last3Average, double previous3Average, double last3ChangePercent,
        List<Anomaly> anomalies) {

    public record Anomaly(int index, double value, double expected, double score) {}
}
