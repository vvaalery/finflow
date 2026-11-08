package com.finance.backend.analytics;

import java.util.Map;

public record ForecastOutput(double[] values, Map<String, Object> params) {}
