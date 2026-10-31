package com.finance.backend.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
}
