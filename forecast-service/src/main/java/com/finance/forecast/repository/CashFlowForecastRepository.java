package com.finance.forecast.repository;

import com.finance.forecast.entity.CashFlowForecast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CashFlowForecastRepository extends JpaRepository<CashFlowForecast, UUID> {
    List<CashFlowForecast> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
}
