package com.finance.backend.repository;

import com.finance.backend.entity.Account;
import com.finance.backend.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByCompany(Company company);
    List<Account> findByCompanyId(UUID companyId);
    List<Account> findByCompanyIdAndIsActiveTrue(UUID companyId);
}