package com.finance.backend.config;

import com.finance.backend.entity.Account;
import com.finance.backend.entity.Company;
import com.finance.backend.entity.Role;
import com.finance.backend.entity.User;
import com.finance.backend.entity.Transaction;
import com.finance.backend.repository.AccountRepository;
import com.finance.backend.repository.CompanyRepository;
import com.finance.backend.repository.RoleRepository;
import com.finance.backend.repository.UserRepository;
import com.finance.backend.repository.TransactionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner seed(RoleRepository roles, UserRepository users, CompanyRepository companies,
                           AccountRepository accounts, TransactionRepository transactions, PasswordEncoder encoder) {
        return args -> {
            Role manager = roles.findByName("FINANCIAL_MANAGER").orElseGet(() -> {
                Role role = new Role(); role.setName("FINANCIAL_MANAGER"); return roles.save(role);
            });
            roles.findByName("DIRECTOR").orElseGet(() -> {
                Role role = new Role(); role.setName("DIRECTOR"); return roles.save(role);
            });
            Role admin = roles.findByName("ADMIN").orElseGet(() -> {
                Role role = new Role(); role.setName("ADMIN"); return roles.save(role);
            });

            User administrator = users.findByEmail("admin@finflow.local").orElseGet(() -> {
                User user = new User();
                user.setEmail("admin@finflow.local");
                user.setFullName("System Administrator");
                user.setPasswordHash(encoder.encode("Admin123!"));
                user.setRole(admin);
                user.setIsActive(true);
                return users.save(user);
            });

            // Restore a usable company profile and accounts only when they are missing.
            Company company = companies.findByOwner(administrator).stream()
                    .filter(item -> "Creative Pulse Design Studio".equalsIgnoreCase(item.getName()))
                    .findFirst()
                    .orElseGet(() -> companies.findAll().stream()
                            .filter(item -> "Creative Pulse Design Studio".equalsIgnoreCase(item.getName()))
                            .findFirst()
                            .map(existing -> {
                                // Reattach the legacy demo company to the seeded admin account.
                                existing.setOwner(administrator);
                                return companies.save(existing);
                            })
                            .orElseGet(() -> {
                                Company item = new Company();
                                item.setName("Creative Pulse Design Studio");
                                item.setTaxId("193456789");
                                item.setAddress("Minsk, Belarus");
                                item.setPhone("+375 29 555-01-20");
                                item.setDescription("Студия цифровых продуктов, дизайна интерфейсов и разработки корпоративных решений.");
                                item.setWebsiteUrl("https://finflow.local");
                                item.setStatus("ACTIVE");
                                item.setOwner(administrator);
                                return companies.save(item);
                            }));

            boolean companyChanged = false;
            if (company.getTaxId() == null || company.getTaxId().isBlank()) { company.setTaxId("193456789"); companyChanged = true; }
            if (company.getAddress() == null || company.getAddress().isBlank()) { company.setAddress("Minsk, Belarus"); companyChanged = true; }
            if (company.getPhone() == null || company.getPhone().isBlank()) { company.setPhone("+375 29 555-01-20"); companyChanged = true; }
            if (company.getDescription() == null || company.getDescription().isBlank()) { company.setDescription("Студия цифровых продуктов, дизайна интерфейсов и разработки корпоративных решений."); companyChanged = true; }
            if (company.getWebsiteUrl() == null || company.getWebsiteUrl().isBlank()) { company.setWebsiteUrl("https://finflow.local"); companyChanged = true; }
            if (company.getStatus() == null || company.getStatus().isBlank()) { company.setStatus("ACTIVE"); companyChanged = true; }
            if (companyChanged) companies.save(company);

            java.util.List<Account> companyAccounts = accounts.findByCompany(company);
            if (companyAccounts.stream().noneMatch(account -> "Основной расчетный счет".equalsIgnoreCase(account.getAccountName()))) {
                Account byn = new Account();
                byn.setCompany(company); byn.setAccountName("Основной расчетный счет");
                byn.setAccountType("BANK"); byn.setCurrency("BYN");
                byn.setBalance(new BigDecimal("289000.00")); byn.setIsActive(true);
                accounts.save(byn);
            }
            if (companyAccounts.stream().noneMatch(account -> "Счет расчетов с иностранными клиентами".equalsIgnoreCase(account.getAccountName()))) {
                Account usd = new Account();
                usd.setCompany(company); usd.setAccountName("Счет расчетов с иностранными клиентами");
                usd.setAccountType("BANK"); usd.setCurrency("USD");
                usd.setBalance(new BigDecimal("60500.00")); usd.setIsActive(true);
                accounts.save(usd);
            }
            companyAccounts = accounts.findByCompany(company);

            // The demo model needs real historical rows; add a compact sample only to an empty history.
            if (transactions.findByAccountCompanyIdAndDateBetween(company.getId(), LocalDate.now().minusMonths(13), LocalDate.now()).isEmpty()) {
                Account primary = companyAccounts.get(0);
                for (int month = 1; month <= 12; month++) {
                    LocalDate monthDate = LocalDate.now().minusMonths(month);
                    Transaction income = new Transaction();
                    income.setAccount(primary); income.setTransactionType("INCOME");
                    income.setAmount(new BigDecimal("" + (42000 + month * 1250) + ".00"));
                    income.setDate(monthDate.withDayOfMonth(10));
                    income.setDescription("Поступления по договорам и проектам");
                    income.setCategory("Выручка"); income.setCounterparty("Корпоративные клиенты");
                    transactions.save(income);

                    Transaction expense = new Transaction();
                    expense.setAccount(primary); expense.setTransactionType("EXPENSE");
                    expense.setAmount(new BigDecimal("" + (24500 + month * 620) + ".00"));
                    expense.setDate(monthDate.withDayOfMonth(20));
                    expense.setDescription("Операционные расходы компании");
                    expense.setCategory("Операционные расходы"); expense.setCounterparty("Поставщики и подрядчики");
                    transactions.save(expense);
                }
            }
        };
    }
}
