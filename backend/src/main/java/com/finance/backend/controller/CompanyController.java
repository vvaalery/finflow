package com.finance.backend.controller;

import com.finance.backend.dto.request.CompanyRequest;
import com.finance.backend.dto.response.CompanyResponse;
import com.finance.backend.entity.User;
import com.finance.backend.service.CompanyService;
import com.finance.internal.security.RestGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;
    private final RestGuard guard;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER','DIRECTOR')")
    public List<CompanyResponse> getCompanies() {
        User u = guard.user();
        if (guard.policy().canReadAll(u)) return companyService.getAllCompanies();
        return companyService.getCompaniesByOwner(u.getId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER','DIRECTOR')")
    public CompanyResponse getCompanyById(@PathVariable UUID id) {
        guard.readCompany(id);
        return companyService.getCompanyById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse createCompany(
            @RequestParam(required = false) UUID ownerId,
            @Valid @RequestBody CompanyRequest request) {
        User u = guard.user();
        UUID owner = ownerId == null ? u.getId() : ownerId;
        if (!owner.equals(u.getId()) && !guard.policy().isAdmin(u)) {
            throw new AccessDeniedException("Нельзя создавать компанию от имени другого пользователя");
        }
        return companyService.createCompany(owner, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
    public CompanyResponse updateCompany(
            @PathVariable UUID id,
            @Valid @RequestBody CompanyRequest request) {
        guard.writeCompany(id);
        return companyService.updateCompany(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCompany(@PathVariable UUID id) {
        guard.writeCompany(id);
        companyService.deleteCompany(id);
    }
}
