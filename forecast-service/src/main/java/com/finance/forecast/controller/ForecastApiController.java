package com.finance.forecast.controller;

import com.finance.forecast.client.FinanceClient;
import com.finance.forecast.dto.ForecastResponse;
import com.finance.forecast.service.ForecastCalculationService;
import com.finance.forecast.service.ForecastOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
public class ForecastApiController {
    private final FinanceClient finance;
    private final ForecastOrchestrator orchestrator;
    private final ForecastCalculationService calculation;

    @GetMapping("/generate")
    public ForecastResponse generate(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @RequestParam UUID companyId,
            @RequestParam(defaultValue = "6") int months,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String scenario,
            @RequestParam(required = false) String strategy,
            @RequestParam(required = false) Double multiplier) {
        finance.requireAccess(companyId, auth);
        String m = method != null ? method : strategy;
        String s = scenario != null ? scenario : ForecastOrchestrator.legacyScenario(strategy, multiplier);
        return orchestrator.generate(companyId, months, m, s, "user-request");
    }

    @GetMapping("/latest")
    public ForecastResponse latest(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @RequestParam UUID companyId) {
        finance.requireAccess(companyId, auth);
        return calculation.latest(companyId);
    }

    @GetMapping("/history")
    public List<ForecastResponse> history(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @RequestParam UUID companyId) {
        finance.requireAccess(companyId, auth);
        return calculation.history(companyId);
    }

    @GetMapping("/compare")
    public List<ForecastResponse.ModelScore> compare(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @RequestParam UUID companyId,
            @RequestParam(required = false) String method) {
        finance.requireAccess(companyId, auth);
        return orchestrator.compare(companyId, method);
    }
}
