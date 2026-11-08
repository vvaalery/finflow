package com.finance.backend.analytics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SeriesAnalyzer {
    private SeriesAnalyzer() {}

    public static SeriesStats analyze(double[] y) {
        int n = y.length;
        if (n == 0) return new SeriesStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "FLAT", 0, 0, 0, 0, 0, List.of());
        double mean = Arrays.stream(y).average().orElse(0);
        double var = 0;
        for (double v : y) var += (v - mean) * (v - mean);
        double std = n > 1 ? Math.sqrt(var / (n - 1)) : 0;
        double[] sorted = y.clone();
        Arrays.sort(sorted);
        double median = n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;

        Ols ols = Ols.fit(y, 0, n);
        double slopePct = Math.abs(mean) < 1e-9 ? 0 : ols.slope / mean * 100;
        String trend = n >= 4 && Math.abs(ols.tStat) >= 2 ? (ols.slope > 0 ? "UP" : "DOWN") : "FLAT";

        double[] resid = new double[n];
        for (int i = 0; i < n; i++) resid[i] = y[i] - ols.at(i);

        double last3 = avg(y, Math.max(0, n - 3), n);
        double prev3 = n >= 6 ? avg(y, n - 6, n - 3) : 0;
        double change = prev3 > 1e-9 ? (last3 - prev3) / prev3 * 100 : 0;

        return new SeriesStats(n, mean, median, std, Math.abs(mean) < 1e-9 ? 0 : std / Math.abs(mean),
                sorted[0], sorted[n - 1], ols.slope, slopePct, ols.r2, trend,
                autocorrelation(resid, 1), seasonalStrength(resid), last3, prev3, change, anomalies(y, resid, ols));
    }

    private static double avg(double[] y, int from, int to) {
        double s = 0;
        for (int i = from; i < to; i++) s += y[i];
        return to > from ? s / (to - from) : 0;
    }

    static double autocorrelation(double[] x, int lag) {
        int n = x.length;
        if (n <= lag + 1) return 0;
        double mean = Arrays.stream(x).average().orElse(0), den = 0, num = 0;
        for (double v : x) den += (v - mean) * (v - mean);
        if (den < 1e-12) return 0;
        for (int i = lag; i < n; i++) num += (x[i] - mean) * (x[i - lag] - mean);
        return num / den;
    }

    static double seasonalStrength(double[] detrended) {
        int n = detrended.length;
        if (n < 24) return 0;
        double[] sum = new double[12]; int[] cnt = new int[12];
        for (int i = 0; i < n; i++) { sum[i % 12] += detrended[i]; cnt[i % 12]++; }
        double total = 0, rest = 0;
        for (int i = 0; i < n; i++) {
            double s = sum[i % 12] / cnt[i % 12];
            total += detrended[i] * detrended[i];
            rest += (detrended[i] - s) * (detrended[i] - s);
        }
        return total < 1e-12 ? 0 : Math.max(0, Math.min(1, 1 - rest / total));
    }

    private static List<SeriesStats.Anomaly> anomalies(double[] y, double[] resid, Ols ols) {
        int n = resid.length;
        if (n < 6) return List.of();
        double[] sorted = resid.clone();
        Arrays.sort(sorted);
        double med = sorted[n / 2];
        double[] dev = new double[n];
        for (int i = 0; i < n; i++) dev[i] = Math.abs(resid[i] - med);
        double[] ds = dev.clone();
        Arrays.sort(ds);
        double mad = ds[n / 2];
        double scale = mad > 1e-9 ? mad / 0.6745 : ols.residualStd;
        if (scale < 1e-9) return List.of();
        List<SeriesStats.Anomaly> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double z = (resid[i] - med) / scale;
            if (Math.abs(z) > 3.5) out.add(new SeriesStats.Anomaly(i, y[i], ols.at(i) + med, z));
        }
        return out;
    }
}
