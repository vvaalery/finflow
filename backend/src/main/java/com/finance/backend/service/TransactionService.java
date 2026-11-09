package com.finance.backend.service;

import com.finance.backend.dto.request.TransactionRequest;
import com.finance.backend.dto.response.TransactionResponse;
import com.finance.backend.entity.Account;
import com.finance.backend.entity.ReferenceCashFlowItem;
import com.finance.backend.entity.ReferenceCounterparty;
import com.finance.backend.entity.Transaction;
import com.finance.backend.repository.AccountRepository;
import com.finance.backend.repository.ReferenceCashFlowItemRepository;
import com.finance.backend.repository.ReferenceCounterpartyRepository;
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
    private final ReferenceCashFlowItemRepository cashFlowItemRepository;
    private final ReferenceCounterpartyRepository counterpartyRepository;

    @Transactional
    public TransactionResponse createTransaction(TransactionRequest request) {
        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new RuntimeException("Счет не найден"));

        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        transaction.setTransactionType(request.getType());
        transaction.setAmount(request.getAmount());
        transaction.setDescription(request.getDescription());
        transaction.setDate(request.getDate() != null ? request.getDate() : LocalDate.now());

        if (request.getCfoId() != null) {
            transaction.setCfoId(request.getCfoId());
        }

        ReferenceCashFlowItem cashFlowItem = null;

        if (request.getCashFlowItemId() != null) {
            cashFlowItem = cashFlowItemRepository.findById(request.getCashFlowItemId())
                    .orElseThrow(() -> new RuntimeException("Статья ДДС не найдена"));
        } else if (request.getCategory() != null && !request.getCategory().isBlank()) {
            cashFlowItem = cashFlowItemRepository.findAll().stream()
                    .filter(item -> item.getName().equalsIgnoreCase(request.getCategory()))
                    .filter(item -> item.getType().equalsIgnoreCase(request.getType()))
                    .findFirst()
                    .orElse(null);
        }

        if (cashFlowItem != null) {
            if (!cashFlowItem.getType().equalsIgnoreCase(request.getType())) {
                throw new RuntimeException("Тип статьи ДДС не соответствует типу операции");
            }

            transaction.setCashFlowItem(cashFlowItem);
            transaction.setCategory(cashFlowItem.getName());
        } else {
            transaction.setCategory(request.getCategory());
        }

        ReferenceCounterparty counterparty = null;

        if (request.getCounterpartyId() != null) {
            counterparty = counterpartyRepository.findById(request.getCounterpartyId())
                    .orElseThrow(() -> new RuntimeException("Контрагент не найден"));
        } else if (request.getCounterparty() != null && !request.getCounterparty().isBlank()) {
            counterparty = counterpartyRepository.findAll().stream()
                    .filter(item -> item.getName().equalsIgnoreCase(request.getCounterparty()))
                    .findFirst()
                    .orElse(null);
        }

        if (counterparty != null) {
            transaction.setCounterpartyRef(counterparty);
            transaction.setCounterparty(counterparty.getName());
        } else {
            transaction.setCounterparty(request.getCounterparty());
        }

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
        List<Transaction> transactions = transactionRepository.findByAccountCompanyIdAndDateBetween(
                ownerId,
                LocalDate.now().minusYears(1),
                LocalDate.now()
        );

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
        response.setCounterparty(transaction.getCounterparty());

        if (transaction.getCfoId() != null) {
            response.setCfoId(transaction.getCfoId().toString());
        }

        if (transaction.getCashFlowItem() != null) {
            response.setCashFlowItemId(transaction.getCashFlowItem().getId());
        }

        if (transaction.getCounterpartyRef() != null) {
            response.setCounterpartyId(transaction.getCounterpartyRef().getId());
        }

        return response;
    }
}