package com.finance.backend.controller;

import com.finance.backend.analytics.ForecastMethod;
import com.finance.backend.analytics.Scenario;
import com.finance.backend.dto.response.ForecastResponse;
import com.finance.backend.service.ForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER','DIRECTOR')")
@RestController
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
public class ForecastController {

    private final ForecastService forecastService;
    @GetMapping("/generate")
    public ForecastResponse generateForecast(@RequestParam UUID companyId,
                                             @RequestParam(defaultValue = "6") int months,
                                             @RequestParam(required = false) String method,
                                             @RequestParam(required = false) String scenario,
                                             @RequestParam(required = false) String strategy,
                                             @RequestParam(required = false) Double multiplier) {
        if (method == null && scenario == null) return forecastService.generateForecast(companyId, months, strategy, multiplier);
        ForecastMethod m = ForecastMethod.parse(method != null ? method : strategy);
        Scenario s = scenario != null ? ForecastService.parseScenario(scenario)
                : (multiplier == null ? Scenario.BASE : multiplier > 1 ? Scenario.OPTIMISTIC : multiplier < 1 ? Scenario.PESSIMISTIC : Scenario.BASE);
        return forecastService.generateForecast(companyId, months, m, s);
    }

    @GetMapping("/latest")
    public ForecastResponse latest(@RequestParam UUID companyId) { return forecastService.getLatestForecast(companyId); }

    @GetMapping("/history")
    public List<ForecastResponse> history(@RequestParam UUID companyId) { return forecastService.getForecastHistory(companyId); }

    @GetMapping("/compare")
    public List<ForecastResponse.ModelScore> compare(@RequestParam UUID companyId,
                                                     @RequestParam(required = false) String method) {
        return forecastService.compareMethods(companyId, ForecastMethod.parse(method));
    }
}
