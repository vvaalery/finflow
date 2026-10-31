package com.finance.backend.controller;

import com.finance.backend.dto.response.ForecastResponse;
import com.finance.backend.service.ForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

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
                                              @RequestParam(defaultValue = "linearRegression") String strategy,
                                              @RequestParam(required = false, defaultValue = "1.0") Double multiplier) {
        return forecastService.generateForecast(companyId, months, strategy, multiplier);
    }
}