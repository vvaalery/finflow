package com.finance.backend.analytics;

import java.time.YearMonth;
import java.util.Map;

public final class LinearTrendForecaster implements Forecaster {
    @Override public String code() { return "LINEAR_REGRESSION"; }

    @Override
    public ForecastOutput forecast(double[] y, YearMonth start, int horizon) {
        int n = y.length;
        Ols ols = Ols.fit(y, Math.max(0, n - 24), n);
        double[] out = new double[horizon];
        for (int i = 0; i < horizon; i++) out[i] = Math.max(0, ols.at(n + i));
        return new ForecastOutput(out, Map.of(
                "slopePerMonth", round(ols.slope), "r2", round(ols.r2), "window", Math.min(n, 24)));
    }

    private static double round(double v) { return Math.round(v * 10000d) / 10000d; }
}
