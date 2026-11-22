package com.finance.internal.security;

import com.finance.backend.entity.Account;
import com.finance.backend.entity.Company;
import com.finance.backend.entity.User;
import com.finance.backend.repository.AccountRepository;
import com.finance.backend.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RestGuard {
    private final AccessPolicy policy;
    private final CompanyRepository companies;
    private final AccountRepository accounts;

    public AccessPolicy policy() { return policy; }

    public User user() { return policy.currentUser(); }

    public Company readCompany(UUID id) {
        User u = policy.currentUser();
        Company c = companies.findById(id).orElseThrow(() -> new NoSuchElementException("Компания не найдена"));
        policy.requireReadCompany(u, c);
        return c;
    }

    public Company writeCompany(UUID id) {
        User u = policy.currentUser();
        Company c = companies.findById(id).orElseThrow(() -> new NoSuchElementException("Компания не найдена"));
        policy.requireWriteCompany(u, c);
        return c;
    }

    public Account readAccount(UUID id) {
        User u = policy.currentUser();
        Account a = accounts.findById(id).orElseThrow(() -> new NoSuchElementException("Счёт не найден"));
        policy.requireReadCompany(u, a.getCompany());
        return a;
    }

    public Account writeAccount(UUID id) {
        User u = policy.currentUser();
        Account a = accounts.findById(id).orElseThrow(() -> new NoSuchElementException("Счёт не найден"));
        policy.requireWriteCompany(u, a.getCompany());
        return a;
    }

    public void readCompanyOrOwner(UUID id) {
        User u = policy.currentUser();
        Optional<Company> company = companies.findById(id);
        if (company.isPresent()) {
            policy.requireReadCompany(u, company.get());
            return;
        }
        if (!(policy.canReadAll(u) || u.getId().equals(id))) {
            throw new AccessDeniedException("Нет доступа к данным другого пользователя");
        }
    }
}
