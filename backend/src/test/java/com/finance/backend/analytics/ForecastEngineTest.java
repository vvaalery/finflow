package com.finance.backend.analytics;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ForecastEngineTest {
    private static final YearMonth START = YearMonth.of(2023, 1);

    private static double[] series(int n, double base, double slope, double seasonAmp, double noise, long seed) {
        Random r = new Random(seed);
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            int month = START.plusMonths(i).getMonthValue();
            y[i] = base + slope * i + seasonAmp * Math.sin(2 * Math.PI * (month - 1) / 12.0) + noise * r.nextGaussian();
        }
        return y;
    }

    @Test
    void gradientBoostingFollowsTrend() {
        double[] in = series(24, 50_000, 800, 0, 0, 1), out = series(24, 30_000, 300, 0, 0, 2);
        ForecastEngine.Result r = ForecastEngine.forecast(ForecastMethod.GRADIENT_BOOSTING, Scenario.BASE, in, out, START, 3);
        double expectedIn = 50_000 + 800 * 24;
        assertEquals(expectedIn, r.points().get(0).inflow(), expectedIn * 0.03);
        assertTrue(r.points().get(2).inflow() > r.points().get(0).inflow(), "прогноз не должен быть плоским при растущем тренде");
    }

    @Test
    void gradientBoostingCapturesSeasonality() {
        double[] full = series(48, 60_000, 0, 9_000, 0, 3);
        double[] train = Arrays.copyOf(full, 36);
        double[] f = ForecastMethod.GRADIENT_BOOSTING.forecaster.forecast(train, START, 12).values();
        double err = 0, den = 0;
        for (int i = 0; i < 12; i++) { err += Math.abs(f[i] - full[36 + i]); den += full[36 + i]; }
        assertTrue(err / den < 0.06, "WAPE на сезонном ряду должен быть < 6%");
    }

    @Test
    void scenariosScaleInflowAndOutflow() {
        double[] in = series(12, 50_000, 0, 0, 0, 1), out = series(12, 30_000, 0, 0, 0, 2);
        var base = ForecastEngine.forecast(ForecastMethod.MOVING_AVERAGE, Scenario.BASE, in, out, START, 1).points().get(0);
        var pess = ForecastEngine.forecast(ForecastMethod.MOVING_AVERAGE, Scenario.PESSIMISTIC, in, out, START, 1).points().get(0);
        assertEquals(base.inflow() * 0.85, pess.inflow(), 1e-6);
        assertEquals(base.outflow() * 1.10, pess.outflow(), 1e-6);
    }

    @Test
    void intervalsContainPointForecast() {
        double[] in = series(24, 50_000, 500, 0, 2_000, 5), out = series(24, 30_000, 200, 0, 1_500, 6);
        for (ForecastMethod m : ForecastMethod.values())
            for (var p : ForecastEngine.forecast(m, Scenario.BASE, in, out, START, 6).points()) {
                assertTrue(p.netLow() <= p.net() && p.net() <= p.netHigh());
                assertTrue(p.inflow() >= 0 && p.outflow() >= 0);
            }
    }

    @Test
    void tooShortHistoryIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ForecastMethod.GRADIENT_BOOSTING.forecaster.forecast(new double[5], START, 3));
    }

    @Test
    void analyzerDetectsTrendAndOutlier() {
        double[] y = series(24, 50_000, 500, 0, 1_000, 7);
        y[15] += 40_000;
        SeriesStats s = SeriesAnalyzer.analyze(y);
        assertEquals("UP", s.trend());
        assertTrue(s.anomalies().stream().anyMatch(a -> a.index() == 15));
    }
}
