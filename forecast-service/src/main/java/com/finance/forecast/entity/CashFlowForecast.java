package com.finance.forecast.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "cash_flow_forecasts",
    indexes = {
        @Index(name = "idx_cash_flow_forecasts_company_created", columnList = "company_id, created_at")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "forecast_period_start", nullable = false)
    private LocalDate forecastPeriodStart;

    @Column(name = "forecast_period_end", nullable = false)
    private LocalDate forecastPeriodEnd;

    @Column(name = "total_projected_inflow")
    private BigDecimal totalProjectedInflow = BigDecimal.ZERO;

    @Column(name = "total_projected_outflow")
    private BigDecimal totalProjectedOutflow = BigDecimal.ZERO;

    @Column(name = "net_cash_flow")
    private BigDecimal netCashFlow = BigDecimal.ZERO;

    @Column(name = "strategy_used")
    private String strategyUsed;

    @Column(name = "scenario")
    private String scenario = "BASE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private JsonNode parameters;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
