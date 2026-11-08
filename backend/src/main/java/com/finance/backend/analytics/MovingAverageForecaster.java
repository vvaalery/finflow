package com.finance.backend.analytics;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.Map;

public final class MovingAverageForecaster implements Forecaster {
    private static final int WINDOW = 3;

    @Override public String code() { return "MOVING_AVERAGE"; }

    @Override
    public ForecastOutput forecast(double[] y, YearMonth start, int horizon) {
        int w = Math.min(WINDOW, y.length);
        double num = 0, den = 0;
        for (int k = 0; k < w; k++) {
            double weight = k + 1;
            num += weight * y[y.length - w + k];
            den += weight;
        }
        double level = Math.max(0, den == 0 ? 0 : num / den);
        double[] out = new double[horizon];
        Arrays.fill(out, level);
        return new ForecastOutput(out, Map.of("window", w, "weights", "1-2-3"));
    }
}
