package com.finance.backend.analytics;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HoltWintersForecaster implements Forecaster {
    private static final int SEASON = 12;

    @Override public String code() { return "HOLT_WINTERS"; }

    private static final class Fit {
        double level, trend, sse = Double.POSITIVE_INFINITY; double[] seas;
        double a, b, g, phi;
    }

    @Override
    public ForecastOutput forecast(double[] y, YearMonth start, int horizon) {
        int n = y.length;
        boolean seasonal = n >= 2 * SEASON;
        Fit best = new Fit();
        double[] alphas = {0.1, 0.2, 0.3, 0.5, 0.7, 0.9};
        double[] betas = {0.01, 0.05, 0.1, 0.2, 0.3};
        double[] gammas = seasonal ? new double[]{0.1, 0.3, 0.5} : new double[]{0};
        double[] phis = {0.8, 0.9, 0.95, 0.98, 1.0};
        for (double a : alphas) for (double b : betas) for (double g : gammas) for (double phi : phis) {
            Fit f = run(y, seasonal, a, b, g, phi);
            if (f.sse < best.sse) best = f;
        }
        double[] out = new double[horizon];
        double damp = 0, p = 1;
        for (int h = 1; h <= horizon; h++) {
            p *= best.phi; damp += p;
            double v = best.level + damp * best.trend;
            if (seasonal) v += best.seas[(n - 1 + h) % SEASON];
            out[h - 1] = Math.max(0, v);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("seasonal", seasonal);
        params.put("alpha", best.a); params.put("beta", best.b);
        if (seasonal) params.put("gamma", best.g);
        params.put("phi", best.phi);
        return new ForecastOutput(out, params);
    }

    private static Fit run(double[] y, boolean seasonal, double a, double b, double g, double phi) {
        int n = y.length;
        Fit f = new Fit();
        f.a = a; f.b = b; f.g = g; f.phi = phi;
        int first;
        double level, trend;
        double[] seas = null;
        if (seasonal) {
            double m1 = 0, m2 = 0;
            for (int i = 0; i < SEASON; i++) { m1 += y[i]; m2 += y[SEASON + i]; }
            m1 /= SEASON; m2 /= SEASON;
            level = m1; trend = (m2 - m1) / SEASON;
            seas = new double[SEASON];
            for (int i = 0; i < SEASON; i++) seas[i] = y[i] - m1;
            first = SEASON;
        } else {
            int k = Math.min(2, n - 1);
            level = y[0]; trend = k > 0 ? (y[k] - y[0]) / k : 0;
            first = 1;
        }
        double sse = 0;
        for (int t = first; t < n; t++) {
            double s = seasonal ? seas[t % SEASON] : 0;
            double pred = level + phi * trend + s;
            double err = y[t] - pred;
            sse += err * err;
            double newLevel = a * (y[t] - s) + (1 - a) * (level + phi * trend);
            double newTrend = b * (newLevel - level) + (1 - b) * phi * trend;
            if (seasonal) seas[t % SEASON] = g * (y[t] - newLevel) + (1 - g) * s;
            level = newLevel; trend = newTrend;
        }
        f.level = level; f.trend = trend; f.seas = seas; f.sse = sse;
        return f;
    }
}
