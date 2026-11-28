package com.finance.forecast.controller;

import com.finance.forecast.dto.ForecastRequest;
import com.finance.forecast.dto.ForecastResponse;
import com.finance.forecast.service.ForecastCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/forecasts")
@RequiredArgsConstructor
public class ForecastInternalController {
    private final ForecastCalculationService forecastService;

    @PostMapping("/generate")
    public ForecastResponse generate(@RequestBody ForecastRequest request) {
        return forecastService.generate(request);
    }

    @PostMapping("/compare")
    public List<ForecastResponse.ModelScore> compare(@RequestBody ForecastRequest request) {
        return forecastService.compare(request);
    }

    @GetMapping("/{companyId}/latest")
    public ForecastResponse latest(@PathVariable UUID companyId) {
        return forecastService.latest(companyId);
    }

    @GetMapping("/{companyId}/history")
    public List<ForecastResponse> history(@PathVariable UUID companyId) {
        return forecastService.history(companyId);
    }
}
