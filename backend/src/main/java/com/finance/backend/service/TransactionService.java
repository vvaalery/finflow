package com.finance.backend.service;

import com.finance.backend.dto.request.TransactionRequest;
import com.finance.backend.dto.response.TransactionResponse;
import com.finance.backend.entity.Account;
import com.finance.backend.entity.Transaction;
import com.finance.backend.repository.AccountRepository;
import com.finance.backend.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {
    
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    
    @Transactional
    public TransactionResponse createTransaction(TransactionRequest request) {
        Account account = accountRepository.findById(request.getAccountId())
            .orElseThrow(() -> new RuntimeException("Счет не найден"));
        
        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        transaction.setTransactionType(request.getType());
        transaction.setAmount(request.getAmount());
        transaction.setDescription(request.getDescription());
        transaction.setCategory(request.getCategory());
        transaction.setDate(request.getDate() != null ? request.getDate() : LocalDate.now());
        
        // Добавить cfoId, если есть в запросе
        if (request.getCfoId() != null) {
            transaction.setCfoId(request.getCfoId());
        }
        
        // Обновляем баланс счета
        if ("INCOME".equals(request.getType())) {
            account.setBalance(account.getBalance().add(request.getAmount()));
        } else if ("EXPENSE".equals(request.getType())) {
            account.setBalance(account.getBalance().subtract(request.getAmount()));
        }
        accountRepository.save(account);
        
        Transaction saved = transactionRepository.save(transaction);
        return mapToResponse(saved);
    }
    
    public List<TransactionResponse> getTransactionsByAccountId(UUID accountId) {
        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByDateDesc(accountId);
        return transactions.stream().map(this::mapToResponse).collect(Collectors.toList());
    }
    
    public List<TransactionResponse> getTransactionsByOwnerId(UUID ownerId) {
        List<Transaction> transactions = transactionRepository.findByAccountCompanyIdAndDateBetween(ownerId, LocalDate.now().minusYears(1), LocalDate.now());
        return transactions.stream().map(this::mapToResponse).collect(Collectors.toList());
    }
    
    private TransactionResponse mapToResponse(Transaction transaction) {
        TransactionResponse response = new TransactionResponse();
        response.setId(transaction.getId());
        response.setAccountId(transaction.getAccount().getId());
        response.setAccountName(transaction.getAccount().getAccountName());
        response.setTransactionType(transaction.getTransactionType());
        response.setAmount(transaction.getAmount());
        response.setDate(transaction.getDate());
        response.setDescription(transaction.getDescription());
        response.setCategory(transaction.getCategory());
        
        // Добавить cfoId в ответ
        if (transaction.getCfoId() != null) {
            response.setCfoId(transaction.getCfoId().toString());
        }
        
        return response;
    }
}