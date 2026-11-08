package com.finance.backend.analytics;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ForecastEngine {
    private static final double Z80 = 1.2816;

    private ForecastEngine() {}

    public record Point(YearMonth month, double inflow, double outflow, double net,
                        double inflowLow, double inflowHigh, double outflowLow, double outflowHigh,
                        double netLow, double netHigh) {}

    public record Result(ForecastMethod method, Scenario scenario, List<Point> points,
                         Backtester.Result quality, Map<String, Object> inflowParams, Map<String, Object> outflowParams) {}

    public record Score(ForecastMethod method, Backtester.Result quality) {}

    public static Result forecast(ForecastMethod method, Scenario scenario, double[] inflow, double[] outflow,
                                  YearMonth start, int horizon) {
        Forecaster f = method.forecaster;
        ForecastOutput in = f.forecast(inflow, start, horizon);
        ForecastOutput out = f.forecast(outflow, start, horizon);
        Backtester.Result bt = Backtester.run(f, inflow, outflow, start);

        YearMonth first = start.plusMonths(inflow.length);
        double inMean = mean(inflow), outMean = mean(outflow);
        List<Point> points = new ArrayList<>();
        for (int h = 1; h <= horizon; h++) {
            double pi = in.values()[h - 1] * scenario.inflowFactor;
            double po = out.values()[h - 1] * scenario.outflowFactor;
            double si = sigma(bt.inflow(), h, inMean) * scenario.inflowFactor;
            double so = sigma(bt.outflow(), h, outMean) * scenario.outflowFactor;
            double sn = bt.net().available() ? sigma(bt.net(), h, Math.max(inMean, outMean))
                    * Math.sqrt((Math.pow(scenario.inflowFactor, 2) + Math.pow(scenario.outflowFactor, 2)) / 2) : Math.hypot(si, so);
            double net = pi - po;
            points.add(new Point(first.plusMonths(h - 1), pi, po, net,
                    Math.max(0, pi - Z80 * si), pi + Z80 * si, Math.max(0, po - Z80 * so), po + Z80 * so,
                    net - Z80 * sn, net + Z80 * sn));
        }
        return new Result(method, scenario, points, bt, in.params(), out.params());
    }

    /** Сравнение всех методов по бэктесту (меньше WAPE по чистому потоку — лучше). */
    public static List<Score> compare(double[] inflow, double[] outflow, YearMonth start) {
        List<Score> scores = new ArrayList<>();
        for (ForecastMethod m : ForecastMethod.values())
            scores.add(new Score(m, Backtester.run(m.forecaster, inflow, outflow, start)));
        scores.sort(Comparator.comparingDouble(s -> s.quality().net().available() ? s.quality().net().wape() : Double.MAX_VALUE));
        return scores;
    }

    /** Ошибка на горизонте h по бэктесту; за пределами проверенного горизонта растёт как sqrt(h). */
    private static double sigma(Backtester.Metrics m, int h, double fallbackLevel) {
        if (!m.available()) return 0.15 * fallbackLevel;
        double[] by = m.rmseByHorizon();
        double running = 0;
        int limit = Math.min(h, by.length);
        for (int i = 0; i < limit; i++) if (!Double.isNaN(by[i])) running = Math.max(running, by[i]);
        if (running == 0) running = m.rmse();
        return h <= by.length ? running : running * Math.sqrt((double) h / by.length);
    }

    private static double mean(double[] v) {
        double s = 0;
        for (double d : v) s += d;
        return v.length == 0 ? 0 : s / v.length;
    }
}
