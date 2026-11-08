package com.finance.backend.analytics;

public enum Scenario {
    BASE(1.00, 1.00),
    OPTIMISTIC(1.15, 1.00),
    PESSIMISTIC(0.85, 1.10);

    public final double inflowFactor, outflowFactor;
    Scenario(double in, double out) { this.inflowFactor = in; this.outflowFactor = out; }
}
