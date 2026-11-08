package com.finance.backend.analytics;

import java.util.Locale;

public enum ForecastMethod {
    MOVING_AVERAGE("Скользящее среднее", "Статистический метод", new MovingAverageForecaster()),
    LINEAR_REGRESSION("Линейная регрессия", "Статистический метод", new LinearTrendForecaster()),
    HOLT_WINTERS("Экспоненциальное сглаживание (Хольт-Винтерс)", "Временной ряд", new HoltWintersForecaster()),
    GRADIENT_BOOSTING("Градиентный бустинг", "Машинное обучение", new GradientBoostingForecaster());

    public final String label, family;
    public final Forecaster forecaster;

    ForecastMethod(String label, String family, Forecaster f) { this.label = label; this.family = family; this.forecaster = f; }

    public static ForecastMethod parse(String raw) {
        if (raw == null || raw.isBlank()) return GRADIENT_BOOSTING;
        String s = raw.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (s.contains("moving")) return MOVING_AVERAGE;
        if (s.contains("linear")) return LINEAR_REGRESSION;
        if (s.contains("holt") || s.contains("exponential") || s.contains("timeseries")) return HOLT_WINTERS;
        return GRADIENT_BOOSTING;
    }
}
