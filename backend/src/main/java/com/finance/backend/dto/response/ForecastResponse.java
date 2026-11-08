package com.finance.backend.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class ForecastResponse {
    private UUID id;
    private BigDecimal totalProjectedInflow;
    private BigDecimal totalProjectedOutflow;
    private BigDecimal netCashFlow;
    private String strategyUsed;
    private String period;
    private List<ForecastPointResponse> items = new ArrayList<>();

    private String method;
    private String methodLabel;
    private String methodFamily;
    private String scenario;
    private BigDecimal currentBalance;
    private BigDecimal projectedClosingBalance;
    private List<HistoryPoint> history = new ArrayList<>();
    private Quality quality;
    private List<ModelScore> comparison = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private Map<String, Object> modelParams;

    public record HistoryPoint(LocalDate date, BigDecimal inflow, BigDecimal outflow, BigDecimal netCashFlow) {}

    public record Quality(boolean available, int windows, Double mae, Double rmse, Double wape, String note) {}

    public record ModelScore(String method, String label, String family, boolean selected, boolean best,
                             Double wape, Double mae, Double rmse) {}
}
