package com.finance.backend.service;

import com.finance.backend.dto.request.AccountRequest;
import com.finance.backend.dto.response.AccountResponse;
import com.finance.backend.entity.Account;
import com.finance.backend.entity.Company;
import com.finance.backend.repository.AccountRepository;
import com.finance.backend.repository.CompanyRepository;
import com.finance.paymentservice.client.CompanyServiceGateway.AccountDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final CompanyDirectoryService directory;

    @Transactional
    public AccountResponse createAccount(UUID companyId, AccountRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Компания не найдена"));

        AccountDto draft = new AccountDto(null, company.getId(), request.getAccountName(),
                request.getAccountType(), request.getCurrency(), BigDecimal.ZERO, true);

        return mapToResponse(directory.createAccount(draft));
    }

    public List<AccountResponse> getAccountsByCompanyId(UUID companyId) {
        List<Account> accounts = accountRepository.findByCompanyId(companyId);
        return accounts.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<AccountResponse> getAccountsByOwnerId(UUID ownerId) {
        List<Account> accounts = accountRepository.findByCompanyId(ownerId);
        return accounts.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional
    public void deleteAccount(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Счёт не найден"));
        directory.deleteAccount(account);
    }

    private AccountResponse mapToResponse(Account account) {
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setAccountName(account.getAccountName());
        response.setAccountType(account.getAccountType());
        response.setCurrency(account.getCurrency());
        response.setBalance(account.getBalance());
        response.setCompanyId(account.getCompany().getId());
        response.setCompanyName(account.getCompany().getName());
        return response;
    }
}
