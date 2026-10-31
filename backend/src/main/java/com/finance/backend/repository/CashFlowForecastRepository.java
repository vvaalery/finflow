package com.finance.backend.repository;

import com.finance.backend.entity.CashFlowForecast;
import com.finance.backend.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CashFlowForecastRepository extends JpaRepository<CashFlowForecast, UUID> {
    List<CashFlowForecast> findByCompany(Company company);
    List<CashFlowForecast> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
}