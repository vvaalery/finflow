package com.finance.backend.analytics;

import java.time.YearMonth;

public interface Forecaster {
    String code();
    ForecastOutput forecast(double[] y, YearMonth start, int horizon);
}
