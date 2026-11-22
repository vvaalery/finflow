package com.finance.backend.controller;

import com.finance.backend.dto.response.ImportResult;
import com.finance.backend.service.ImportService;
import com.finance.internal.security.RestGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;
    private final RestGuard guard;

    @PostMapping("/transactions")
    public ImportResult importTransactions(@RequestParam("file") MultipartFile file,
                                           @RequestParam UUID accountId) {
        guard.writeAccount(accountId);
        return importService.importTransactions(file, accountId);
    }

    @PostMapping("/payments")
    public ImportResult importPayments(@RequestParam("file") MultipartFile file,
                                       @RequestParam UUID companyId) {
        guard.writeCompany(companyId);
        return importService.importPayments(file, companyId);
    }
}
