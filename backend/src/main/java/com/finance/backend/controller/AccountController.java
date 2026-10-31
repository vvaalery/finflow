package com.finance.backend.controller;

import com.finance.backend.dto.request.AccountRequest;
import com.finance.backend.dto.response.AccountResponse;
import com.finance.backend.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {
    
    private final AccountService accountService;
    
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER','DIRECTOR')")
    public List<AccountResponse> getAccounts(@RequestParam(required = false) UUID companyId,
                                              @RequestParam(required = false) UUID ownerId) {
        if (companyId != null) {
            return accountService.getAccountsByCompanyId(companyId);
        } else if (ownerId != null) {
            return accountService.getAccountsByOwnerId(ownerId);
        }
        return List.of();
    }
    
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(@RequestParam UUID companyId,
                                          @Valid @RequestBody AccountRequest request) {
        return accountService.createAccount(companyId, request);
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@PathVariable UUID id) {
        accountService.deleteAccount(id);
    }
}