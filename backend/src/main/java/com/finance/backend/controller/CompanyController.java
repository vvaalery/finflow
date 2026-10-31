package com.finance.backend.controller;

import com.finance.backend.dto.request.CompanyRequest;
import com.finance.backend.dto.response.CompanyResponse;
import com.finance.backend.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {
    
    private final CompanyService companyService;
    
    
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER','DIRECTOR')")
    public List<CompanyResponse> getCompanies(@RequestParam UUID ownerId, Authentication authentication) {
        boolean director = authentication.getAuthorities().stream().anyMatch(authority -> "ROLE_DIRECTOR".equals(authority.getAuthority()));
        return director ? companyService.getAllCompanies() : companyService.getCompaniesByOwner(ownerId);
    }
    
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER','DIRECTOR')")
    public CompanyResponse getCompanyById(@PathVariable UUID id) {
        return companyService.getCompanyById(id);
    }
    
    
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse createCompany(
            @RequestParam UUID ownerId,
            @Valid @RequestBody CompanyRequest request) {
        return companyService.createCompany(ownerId, request);
    }
    
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
    public CompanyResponse updateCompany(
            @PathVariable UUID id,
            @Valid @RequestBody CompanyRequest request) {
        return companyService.updateCompany(id, request);
    }
    
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCompany(@PathVariable UUID id) {
        companyService.deleteCompany(id);
    }
}