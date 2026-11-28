package com.finance.internal.controller;

import com.finance.backend.security.ServiceTokenFilter;
import com.finance.backend.service.CashFlowHistoryService;
import com.finance.internal.security.RestGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/companies/{companyId}")
@RequiredArgsConstructor
public class InternalHistoryController {

    public record CashFlowHistoryDto(
            YearMonth start,
            double[] inflow,
            double[] outflow,
            int excludedOtherCurrency,
            Map<String, Double> expenseByCategory
    ) {}

    private final CashFlowHistoryService history;
    private final RestGuard guard;

    @GetMapping("/cash-flow-history")
    public CashFlowHistoryDto cashFlowHistory(@PathVariable UUID companyId) {
        if (!ServiceTokenFilter.isService()) {
            guard.readCompany(companyId);
        }

        CashFlowHistoryService.History h = history.load(companyId);

        return new CashFlowHistoryDto(
                h.start(),
                h.inflow(),
                h.outflow(),
                h.excludedOtherCurrency(),
                h.expenseByCategory()
        );
    }

    @GetMapping("/access")
    public ResponseEntity<Void> access(@PathVariable UUID companyId) {
        guard.readCompany(companyId);
        return ResponseEntity.noContent().build();
    }
}
