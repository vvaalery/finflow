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
}
