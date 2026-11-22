package com.finance.backend.controller;

import com.finance.backend.service.ExportService;
import com.finance.internal.security.RestGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER','DIRECTOR')")
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private final RestGuard guard;

    @GetMapping("/transactions")
    public ResponseEntity<byte[]> exportTransactions(@RequestParam UUID accountId) {
        guard.readAccount(accountId);
        byte[] excel = exportService.exportTransactions(accountId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transactions.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excel);
    }

    @GetMapping("/payments")
    public ResponseEntity<byte[]> exportPayments(@RequestParam UUID companyId) {
        guard.readCompany(companyId);
        byte[] excel = exportService.exportPayments(companyId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payments.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excel);
    }

    @GetMapping("/forecast")
    public ResponseEntity<byte[]> exportForecast(@RequestParam UUID companyId,
                                                  @RequestParam(defaultValue = "6") int months,
                                                  @RequestParam(defaultValue = "linearRegression") String strategy) {
        guard.readCompany(companyId);
        byte[] excel = exportService.exportForecast(companyId, months, strategy);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=forecast.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excel);
    }
}
