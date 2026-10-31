package com.finance.backend.repository;

import com.finance.backend.entity.Company;
import com.finance.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    List<Company> findByOwner(User owner);
    List<Company> findByOwnerId(UUID ownerId);
    boolean existsByNameAndOwner(String name, User owner);
}