package com.finance.backend.service;

import com.finance.backend.entity.Account;
import com.finance.backend.entity.Company;
import com.finance.backend.repository.AccountRepository;
import com.finance.backend.repository.CompanyRepository;
import com.finance.paymentservice.client.CompanyServiceGateway;
import com.finance.paymentservice.client.CompanyServiceGateway.AccountDto;
import com.finance.paymentservice.client.CompanyServiceGateway.CompanyDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyDirectoryService {

    private final CompanyServiceGateway owner;
    private final CompanyRepository companies;
    private final AccountRepository accounts;
    private final JdbcTemplate jdbc;

    @Transactional
    public Company createCompany(CompanyDto draft) {
        UUID id = draft.id() == null ? UUID.randomUUID() : draft.id();
        CompanyDto saved = owner.saveCompany(new CompanyDto(id, draft.name(), draft.taxId(), draft.address(),
                draft.phone(), draft.ownerId(), draft.description(), draft.websiteUrl(), draft.logoUrl(),
                draft.status()));
        upsertCompany(saved);
        log.info("Компания создана в Company Service: companyId={}", saved.id());
        return companies.findById(saved.id()).orElseThrow();
    }

    @Transactional
    public Company updateCompany(Company local, CompanyDto changes) {
        CompanyDto saved = owner.saveCompany(changes);
        local.setName(saved.name());
        local.setTaxId(saved.taxId());
        local.setAddress(saved.address());
        local.setPhone(saved.phone());
        local.setDescription(saved.description());
        local.setWebsiteUrl(saved.websiteUrl());
        local.setLogoUrl(saved.logoUrl());
        local.setStatus(saved.status());
        log.info("Компания обновлена: companyId={}", saved.id());
        return companies.save(local);
    }

    @Transactional
    public void deleteCompany(Company company) {
        companies.delete(company);
        companies.flush();
        owner.deleteCompany(company.getId());
        log.info("Компания удалена: companyId={}", company.getId());
    }

    @Transactional
    public Account createAccount(AccountDto draft) {
        UUID id = draft.id() == null ? UUID.randomUUID() : draft.id();
        AccountDto saved = owner.saveAccount(new AccountDto(id, draft.companyId(), draft.accountName(),
                draft.accountType(), draft.currency(), draft.balance(), draft.active()));
        upsertAccount(saved);
        log.info("Счёт создан в Company Service: accountId={}, companyId={}", saved.id(), saved.companyId());
        return accounts.findById(saved.id()).orElseThrow();
    }

    @Transactional
    public Account updateAccount(Account local, String name, String type, String currency) {
        AccountDto saved = owner.saveAccount(new AccountDto(local.getId(), local.getCompany().getId(), name, type,
                currency, local.getBalance(), Boolean.TRUE.equals(local.getIsActive())));
        copy(local, saved);
        log.info("Счёт обновлён: accountId={}", saved.id());
        return accounts.save(local);
    }

    @Transactional
    public void deleteAccount(Account account) {
        accounts.delete(account);
        accounts.flush();
        owner.deleteAccount(account.getId());
        log.info("Счёт удалён: accountId={}", account.getId());
    }

    public BigDecimal applyBalance(Account account, BigDecimal delta, UUID operationId) {
        BigDecimal after = owner.changeBalance(operationId, account.getId(), delta);
        account.setBalance(after);
        log.info("Баланс изменён: accountId={}, delta={}, balanceAfter={}, operationId={}",
                account.getId(), delta.toPlainString(), after.toPlainString(), operationId);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            UUID accountId = account.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_ROLLED_BACK) {
                        return;
                    }
                    try {
                        UUID compensation = UUID.nameUUIDFromBytes(
                                (operationId + ":rollback").getBytes(StandardCharsets.UTF_8));
                        owner.changeBalance(compensation, accountId, delta.negate());
                        log.warn("Локальная транзакция отменена, баланс счёта {} возвращён", accountId);
                    } catch (RuntimeException e) {
                        log.error("Не удалось компенсировать изменение баланса счёта {} (operationId={})",
                                accountId, operationId, e);
                    }
                }
            });
        }
        return after;
    }

    public Account syncAccount(Account local) {
        try {
            AccountDto dto = owner.getAccount(local.getId())
                    .orElseThrow(() -> new NoSuchElementException("Счёт не найден в Company Service"));
            copy(local, dto);
        } catch (IllegalStateException e) {
            log.warn("Company Service недоступен, счёт {} показан по локальной проекции", local.getId());
        }
        return local;
    }

    public Company syncCompany(Company local) {
        try {
            CompanyDto d = owner.getCompany(local.getId())
                    .orElseThrow(() -> new NoSuchElementException("Компания не найдена в Company Service"));
            local.setName(d.name());
            local.setTaxId(d.taxId());
            local.setAddress(d.address());
            local.setPhone(d.phone());
            local.setDescription(d.description());
            local.setWebsiteUrl(d.websiteUrl());
            local.setLogoUrl(d.logoUrl());
            local.setStatus(d.status());
        } catch (IllegalStateException e) {
            log.warn("Company Service недоступен, компания {} показана по локальной проекции", local.getId());
        }
        return local;
    }

    private void copy(Account a, AccountDto d) {
        a.setAccountName(d.accountName());
        a.setAccountType(d.accountType());
        a.setCurrency(d.currency());
        a.setBalance(d.balance());
        a.setIsActive(d.active());
    }

    private void upsertCompany(CompanyDto c) {
        jdbc.update("""
                INSERT INTO companies (id, name, tax_id, address, phone, owner_id, description, website_url, logo_url, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, tax_id = EXCLUDED.tax_id,
                    address = EXCLUDED.address, phone = EXCLUDED.phone, owner_id = EXCLUDED.owner_id,
                    description = EXCLUDED.description, website_url = EXCLUDED.website_url,
                    logo_url = EXCLUDED.logo_url, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP
                """, c.id(), c.name(), c.taxId(), c.address(), c.phone(), c.ownerId(), c.description(),
                c.websiteUrl(), c.logoUrl(), c.status());
    }

    private void upsertAccount(AccountDto a) {
        jdbc.update("""
                INSERT INTO accounts (id, company_id, account_name, account_type, currency, balance, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET account_name = EXCLUDED.account_name,
                    account_type = EXCLUDED.account_type, currency = EXCLUDED.currency,
                    balance = EXCLUDED.balance, is_active = EXCLUDED.is_active, updated_at = CURRENT_TIMESTAMP
                """, a.id(), a.companyId(), a.accountName(), a.accountType(), a.currency(), a.balance(), a.active());
    }
}
