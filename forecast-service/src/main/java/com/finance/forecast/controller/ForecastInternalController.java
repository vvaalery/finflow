package com.finance.forecast.controller;

import com.finance.forecast.dto.ForecastResponse;
import com.finance.forecast.dto.GenerateCommand;
import com.finance.forecast.dto.RiskResponse;
import com.finance.forecast.service.ForecastCalculationService;
import com.finance.forecast.service.ForecastOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/forecasts")
@RequiredArgsConstructor
public class ForecastInternalController {
    private final ForecastOrchestrator orchestrator;
    private final ForecastCalculationService calculation;

    @PostMapping("/generate")
    public ForecastResponse generate(@RequestBody GenerateCommand c) {
        return orchestrator.generate(c.companyId(), c.months(), c.method(), c.scenario(), "finance-request");
    }

    @PostMapping("/compare")
    public List<ForecastResponse.ModelScore> compare(@RequestBody GenerateCommand c) {
        return orchestrator.compare(c.companyId(), c.method());
    }

    @GetMapping("/{companyId}/latest")
    public ForecastResponse latest(@PathVariable UUID companyId) {
        return calculation.latest(companyId);
    }

    @GetMapping("/{companyId}/history")
    public List<ForecastResponse> history(@PathVariable UUID companyId) {
        return calculation.history(companyId);
    }

    @GetMapping("/{companyId}/risk")
    public RiskResponse risk(@PathVariable UUID companyId) {
        return orchestrator.risk(companyId);
    }
}
