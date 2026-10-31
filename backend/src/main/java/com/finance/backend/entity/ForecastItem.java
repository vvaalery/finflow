package com.finance.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name="forecast_items", indexes=@Index(name="idx_forecast_items_forecast_id", columnList="forecast_id"))
@Data @NoArgsConstructor
public class ForecastItem {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="forecast_id", nullable=false) private CashFlowForecast forecast;
    @Column(name="forecast_date", nullable=false) private LocalDate forecastDate;
    @Column(name="projected_inflow") private BigDecimal projectedInflow=BigDecimal.ZERO;
    @Column(name="projected_outflow") private BigDecimal projectedOutflow=BigDecimal.ZERO;
    @Column(name="actual_inflow") private BigDecimal actualInflow=BigDecimal.ZERO;
    @Column(name="actual_outflow") private BigDecimal actualOutflow=BigDecimal.ZERO;
}
