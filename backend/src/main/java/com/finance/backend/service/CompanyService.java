package com.finance.backend.service;

import com.finance.backend.dto.request.CompanyRequest;
import com.finance.backend.dto.response.CompanyResponse;
import com.finance.backend.entity.Company;
import com.finance.backend.entity.User;
import com.finance.backend.repository.CompanyRepository;
import com.finance.backend.repository.UserRepository;
import com.finance.paymentservice.client.CompanyServiceGateway.CompanyDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyService {
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final CompanyDirectoryService directory;

    @Transactional
    public CompanyResponse createCompany(UUID ownerId, CompanyRequest request) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        CompanyDto draft = new CompanyDto(null, request.getName(), request.getTaxId(), request.getAddress(),
                request.getPhone(), owner.getId(), request.getDescription(), null, request.getLogoUrl(),
                request.getStatus() == null ? "ACTIVE" : request.getStatus());

        return mapToResponse(directory.createCompany(draft));
    }

    @Transactional
    public CompanyResponse updateCompany(UUID id, CompanyRequest request) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Компания не найдена"));

        CompanyDto changes = new CompanyDto(company.getId(), request.getName(), request.getTaxId(),
                request.getAddress(), request.getPhone(), company.getOwner().getId(), request.getDescription(),
                company.getWebsiteUrl(), request.getLogoUrl(),
                request.getStatus() == null ? company.getStatus() : request.getStatus());

        return mapToResponse(directory.updateCompany(company, changes));
    }

    private CompanyResponse mapToResponse(Company company) {
        CompanyResponse response = new CompanyResponse();
        response.setId(company.getId());
        response.setName(company.getName());
        response.setTaxId(company.getTaxId());
        response.setAddress(company.getAddress());
        response.setPhone(company.getPhone());
        response.setDescription(company.getDescription());
        response.setLogoUrl(company.getLogoUrl());
        response.setStatus(company.getStatus());
        response.setOwnerId(company.getOwner().getId());
        response.setOwnerName(company.getOwner().getFullName());
        return response;
    }

    public List<CompanyResponse> getCompaniesByOwner(UUID ownerId) {
        return companyRepository.findByOwnerId(ownerId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<CompanyResponse> getAllCompanies() {
        return companyRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public CompanyResponse getCompanyById(UUID id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Компания не найдена"));
        return mapToResponse(company);
    }

    @Transactional
    public void deleteCompany(UUID id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Компания не найдена"));
        directory.deleteCompany(company);
    }
}
