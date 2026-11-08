package com.finance.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForecastPointResponse {
    private LocalDate date;
    private BigDecimal inflow;
    private BigDecimal outflow;
    private BigDecimal netCashFlow;
    private BigDecimal inflowLow;
    private BigDecimal inflowHigh;
    private BigDecimal outflowLow;
    private BigDecimal outflowHigh;
    private BigDecimal netLow;
    private BigDecimal netHigh;
    private BigDecimal projectedBalance;

    public ForecastPointResponse(LocalDate date, BigDecimal inflow, BigDecimal outflow, BigDecimal netCashFlow) {
        this.date = date; this.inflow = inflow; this.outflow = outflow; this.netCashFlow = netCashFlow;
    }
}
