package com.finance.backend.analytics;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GradientBoostingForecaster implements Forecaster {
    private static final int MIN_HISTORY = 6;
    private static final double DAMPING = 0.98;
    private static final double TREND_SHRINK = 0.5;
    private static final int[] DEPTHS = {1, 2};
    private static final double[] RATES = {0.05, 0.1, 0.2};
    private static final int[] ROUNDS = {20, 50, 100};

    @Override public String code() { return "GRADIENT_BOOSTING"; }

    /** Линия проходит через центр масс окна; наклон сжат пропорционально значимости (soft-gating). */
    private record Trend(double slope, double anchorIndex, double anchorLevel, boolean usesSlope) {
        double at(int i) { return anchorLevel + slope * (i - anchorIndex); }
        double future(int stepsAhead, int lastIndex) {
            double damp = 0, p = 1;
            for (int k = 1; k <= stepsAhead; k++) { p *= DAMPING; damp += p; }
            return at(lastIndex) + slope * damp;
        }
    }

    private record Config(int depth, double rate, int rounds, double cvMse) {}

    @Override
    public ForecastOutput forecast(double[] y, YearMonth start, int horizon) {
        int n = y.length;
        if (n < MIN_HISTORY) throw new IllegalArgumentException("Для градиентного бустинга нужно минимум " + MIN_HISTORY + " месяцев истории");
        Trend trend = fitTrend(y);
        double[] r = new double[n];
        for (int i = 0; i < n; i++) r[i] = y[i] - trend.at(i);

        boolean seasonal = n >= 24;
        int maxLag = seasonal ? 12 : 3;
        int rows = n - maxLag;
        double[][] x = new double[rows][];
        double[] target = new double[rows];
        for (int t = maxLag; t < n; t++) { x[t - maxLag] = features(r, t, start, seasonal); target[t - maxLag] = r[t]; }

        Config cfg = select(x, target);
        GradientBoostingRegressor model = cfg.rounds() == 0 ? null
                : GradientBoostingRegressor.fit(x, target, cfg.rounds(), cfg.rate(), cfg.depth(), 2);

        double[] ext = Arrays.copyOf(r, n + horizon);
        double[] out = new double[horizon];
        for (int i = 0; i < horizon; i++) {
            int t = n + i;
            double residual = model == null ? 0 : model.predict(features(ext, t, start, seasonal));
            ext[t] = residual;
            out[i] = Math.max(0, trend.future(i + 1, n - 1) + residual);
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("trend", trend.usesSlope() ? "damped-linear" : "level");
        params.put("boostingActive", model != null);
        if (model != null) {
            params.put("estimators", model.size());
            params.put("learningRate", cfg.rate());
            params.put("treeDepth", cfg.depth());
        }
        params.put("loss", "huber");
        params.put("seasonalFeatures", seasonal);
        params.put("trainingRows", rows);
        return new ForecastOutput(out, params);
    }

    /**
     * Тренд по последним 24 точкам. Наклон МНК умножается на t²/(t²+0.5): значимый тренд сохраняется почти
     * целиком, а шум, похожий на тренд, подавляется. Константа подобрана Монте-Карло на синтетических рядах.
     */
    private static Trend fitTrend(double[] y) {
        int n = y.length, from = Math.max(0, n - 24);
        Ols ols = Ols.fit(y, from, n);
        double t2 = ols.tStat * ols.tStat;
        double weight = t2 / (t2 + TREND_SHRINK);
        double mx = (from + n - 1) / 2.0, my = 0;
        for (int i = from; i < n; i++) my += y[i];
        my /= (n - from);
        return new Trend(ols.slope * weight, mx, my, weight > 0.5);
    }

    private static double[] features(double[] r, int t, YearMonth start, boolean seasonal) {
        double l1 = r[t - 1], l2 = r[t - 2], l3 = r[t - 3];
        if (!seasonal) return new double[]{l1, l2, l3, (l1 + l2 + l3) / 3};
        int month = start.plusMonths(t).getMonthValue();
        double ang = 2 * Math.PI * (month - 1) / 12.0;
        return new double[]{l1, l2, l3, (l1 + l2 + l3) / 3, r[t - 12], Math.sin(ang), Math.cos(ang)};
    }

    private static Config select(double[][] x, double[] y) {
        int rows = y.length;
        if (rows < 5) return new Config(1, 0.1, 0, 0);
        int folds = Math.min(5, rows - 3);
        double baseline = 0;
        for (int k = 0; k < folds; k++) { double v = y[rows - folds + k]; baseline += v * v; }
        baseline /= folds;
        Config best = new Config(1, 0.1, 0, baseline);
        for (int depth : DEPTHS) for (double rate : RATES) for (int rounds : ROUNDS) {
            double mse = 0;
            for (int k = 0; k < folds; k++) {
                int test = rows - folds + k;
                GradientBoostingRegressor m = GradientBoostingRegressor.fit(
                        Arrays.copyOfRange(x, 0, test), Arrays.copyOfRange(y, 0, test), rounds, rate, depth, 2);
                double e = y[test] - m.predict(x[test]);
                mse += e * e;
            }
            mse /= folds;
            if (mse < best.cvMse() * 0.95 && mse < baseline * 0.95) best = new Config(depth, rate, rounds, mse);
        }
        return best;
    }
}
