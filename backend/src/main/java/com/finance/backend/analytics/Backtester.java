package com.finance.backend.analytics;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public final class Backtester {
    public static final int MAX_HORIZON = 3;
    private static final int MIN_TRAIN = 6;

    private Backtester() {}

    public record Metrics(int observations, double mae, double rmse, double wape, double[] rmseByHorizon) {
        public static Metrics empty() { return new Metrics(0, Double.NaN, Double.NaN, Double.NaN, new double[0]); }
        public boolean available() { return observations > 0; }
    }

    public record Result(Metrics inflow, Metrics outflow, Metrics net, int windows) {
        public boolean available() { return net.available(); }
    }

    public static Result run(Forecaster f, double[] inflow, double[] outflow, YearMonth start) {
        int n = inflow.length;
        int firstOrigin = Math.max(MIN_TRAIN, n - 12);
        if (n - firstOrigin < 1 || n <= MIN_TRAIN) return new Result(Metrics.empty(), Metrics.empty(), Metrics.empty(), 0);

        List<List<Double>> in = lists(), out = lists(), net = lists();
        List<Double> inActual = new ArrayList<>(), outActual = new ArrayList<>(), netDen = new ArrayList<>();
        int windows = 0;
        for (int origin = firstOrigin; origin < n; origin++) {
            int h = Math.min(MAX_HORIZON, n - origin);
            double[] pi, po;
            try {
                pi = f.forecast(java.util.Arrays.copyOfRange(inflow, 0, origin), start, h).values();
                po = f.forecast(java.util.Arrays.copyOfRange(outflow, 0, origin), start, h).values();
            } catch (IllegalArgumentException e) { continue; }
            windows++;
            for (int k = 0; k < h; k++) {
                double ai = inflow[origin + k], ao = outflow[origin + k];
                in.get(k).add(ai - pi[k]);
                out.get(k).add(ao - po[k]);
                net.get(k).add((ai - ao) - (pi[k] - po[k]));
                inActual.add(Math.abs(ai)); outActual.add(Math.abs(ao)); netDen.add(Math.abs(ai));
            }
        }
        return new Result(metrics(in, inActual), metrics(out, outActual), metrics(net, netDen), windows);
    }

    private static List<List<Double>> lists() {
        List<List<Double>> l = new ArrayList<>();
        for (int i = 0; i < MAX_HORIZON; i++) l.add(new ArrayList<>());
        return l;
    }

    private static Metrics metrics(List<List<Double>> byHorizon, List<Double> denominators) {
        int count = 0; double abs = 0, sq = 0;
        double[] rmseH = new double[MAX_HORIZON];
        int usable = 0;
        for (int h = 0; h < MAX_HORIZON; h++) {
            List<Double> e = byHorizon.get(h);
            double s = 0;
            for (double v : e) { abs += Math.abs(v); sq += v * v; s += v * v; count++; }
            rmseH[h] = e.isEmpty() ? Double.NaN : Math.sqrt(s / e.size());
            if (!e.isEmpty()) usable = h + 1;
        }
        if (count == 0) return Metrics.empty();
        double den = denominators.stream().mapToDouble(Double::doubleValue).sum();
        double[] trimmed = java.util.Arrays.copyOf(rmseH, usable);
        return new Metrics(count, abs / count, Math.sqrt(sq / count), den < 1e-9 ? Double.NaN : abs / den, trimmed);
    }
}
