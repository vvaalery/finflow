package com.finance.backend.repository;
import com.finance.backend.entity.ForecastItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface ForecastItemRepository extends JpaRepository<ForecastItem, UUID> { List<ForecastItem> findByForecastIdOrderByForecastDate(UUID forecastId); }
