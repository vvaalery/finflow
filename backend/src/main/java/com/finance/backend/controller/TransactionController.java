package com.finance.backend.controller;

import com.finance.backend.dto.request.TransactionRequest;
import com.finance.backend.dto.response.TransactionResponse;
import com.finance.backend.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER','DIRECTOR')")
    public List<TransactionResponse> getTransactions(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID ownerId) {

        if (accountId != null) {
            return transactionService.getTransactionsByAccountId(accountId);
        } else if (ownerId != null) {
            return transactionService.getTransactionsByOwnerId(ownerId);
        }

        return List.of();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransaction(
            @Valid @RequestBody TransactionRequest request) {
        return transactionService.createTransaction(request);
    }
}