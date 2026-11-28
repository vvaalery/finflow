package com.finance.company;

import com.finance.company.CompanyRepository.AccountRow;
import com.finance.company.CompanyRepository.CompanyRow;
import com.finance.company.CompanyRepository.Ownership;
import com.finance.company.grpc.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CompanyGrpcService extends CompanyServiceGrpc.CompanyServiceImplBase {

    private static final Logger logger = Logger.getLogger(CompanyGrpcService.class.getName());
    private static final Set<String> ACCOUNT_TYPES = Set.of("CASH", "BANK", "CARD", "ELECTRONIC");
    private static final String BASE_CURRENCY = "BYN";

    @FunctionalInterface
    private interface Body<T> {
        T run() throws Exception;
    }

    private final CompanyRepository repository;

    public CompanyGrpcService(CompanyRepository repository) {
        this.repository = repository;
    }

    @Override
    public void getCompany(GetCompanyRequest request, StreamObserver<GetCompanyResponse> out) {
        handle("GetCompany", out, () -> {
            var found = repository.findCompany(uuid(request.getCompanyId(), "companyId"));
            GetCompanyResponse.Builder b = GetCompanyResponse.newBuilder().setFound(found.isPresent());
            found.ifPresent(c -> b.setCompany(toProto(c)));
            return b.build();
        });
    }

    @Override
    public void getAccount(GetAccountRequest request, StreamObserver<GetAccountResponse> out) {
        handle("GetAccount", out, () -> {
            var found = repository.findAccount(uuid(request.getAccountId(), "accountId"));
            GetAccountResponse.Builder b = GetAccountResponse.newBuilder().setFound(found.isPresent());
            found.ifPresent(a -> b.setAccount(toProto(a)));
            return b.build();
        });
    }

    @Override
    public void saveCompany(SaveCompanyRequest request, StreamObserver<CompanyData> out) {
        handle("SaveCompany", out, () -> {
            CompanyData d = request.getCompany();
            if (d.getName().isBlank()) {
                throw new IllegalArgumentException("Название компании обязательно");
            }
            CompanyRow row = new CompanyRow(uuid(d.getId(), "id"), d.getName().trim(), blankToNull(d.getTaxId()),
                    blankToNull(d.getAddress()), blankToNull(d.getPhone()), uuid(d.getOwnerId(), "ownerId"),
                    blankToNull(d.getDescription()), blankToNull(d.getWebsiteUrl()), blankToNull(d.getLogoUrl()),
                    d.getStatus().isBlank() ? "ACTIVE" : d.getStatus());
            return toProto(repository.saveCompany(row));
        });
    }

    @Override
    public void deleteCompany(DeleteRequest request, StreamObserver<DeleteResponse> out) {
        handle("DeleteCompany", out, () -> DeleteResponse.newBuilder()
                .setDeleted(repository.deleteCompany(uuid(request.getId(), "id"))).build());
    }

    @Override
    public void saveAccount(SaveAccountRequest request, StreamObserver<AccountData> out) {
        handle("SaveAccount", out, () -> {
            AccountData d = request.getAccount();
            String type = d.getAccountType().trim().toUpperCase();
            String currency = d.getCurrency().trim().toUpperCase();
            if (d.getAccountName().isBlank()) {
                throw new IllegalArgumentException("Название счёта обязательно");
            }
            if (!ACCOUNT_TYPES.contains(type)) {
                throw new IllegalArgumentException("Тип счёта: CASH, BANK, CARD или ELECTRONIC");
            }
            if (!currency.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException("Валюта — трёхбуквенный код, например BYN");
            }
            AccountRow row = new AccountRow(uuid(d.getId(), "id"), uuid(d.getCompanyId(), "companyId"),
                    d.getAccountName().trim(), type, currency, decimal(d.getBalance()), d.getIsActive());
            return toProto(repository.saveAccount(row));
        });
    }

    @Override
    public void deleteAccount(DeleteRequest request, StreamObserver<DeleteResponse> out) {
        handle("DeleteAccount", out, () -> DeleteResponse.newBuilder()
                .setDeleted(repository.deleteAccount(uuid(request.getId(), "id"))).build());
    }

    @Override
    public void validateAccountOwnership(AccountOwnershipRequest request,
                                         StreamObserver<AccountOwnershipResponse> out) {
        handle("ValidateAccountOwnership", out, () -> {
            Ownership o = repository.ownership(uuid(request.getAccountId(), "accountId"),
                    uuid(request.getCompanyId(), "companyId"));
            return AccountOwnershipResponse.newBuilder()
                    .setAccountExists(o.exists())
                    .setBelongsToCompany(o.belongs())
                    .setMessage(o.belongs() ? "Счёт принадлежит указанной компании"
                            : o.exists() ? "Счёт принадлежит другой компании" : "Счёт не существует")
                    .build();
        });
    }

    @Override
    public void changeBalance(ChangeBalanceRequest request, StreamObserver<ChangeBalanceResponse> out) {
        handle("ChangeBalance", out, () -> {
            BigDecimal delta = decimal(request.getDelta());
            BigDecimal balance = repository.changeBalance(uuid(request.getOperationId(), "operationId"),
                    uuid(request.getAccountId(), "accountId"), delta);
            return ChangeBalanceResponse.newBuilder().setBalance(balance.toPlainString()).build();
        });
    }

    @Override
    public void getForecastInput(ForecastInputRequest request, StreamObserver<ForecastInputResponse> out) {
        handle("GetForecastInput", out, () -> {
            List<AccountRow> accounts = repository.activeAccounts(uuid(request.getCompanyId(), "companyId"));
            BigDecimal base = accounts.stream()
                    .filter(a -> a.currency() == null || BASE_CURRENCY.equalsIgnoreCase(a.currency()))
                    .map(AccountRow::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
            ForecastInputResponse.Builder b = ForecastInputResponse.newBuilder()
                    .setBaseCurrencyBalance(base.toPlainString());
            accounts.forEach(a -> b.addAccounts(toProto(a)));
            return b.build();
        });
    }

    @Override
    public void checkProjectedBalance(ProjectedBalanceRequest request,
                                      StreamObserver<ProjectedBalanceResponse> out) {
        handle("CheckProjectedBalance", out, () -> {
            UUID companyId = uuid(request.getCompanyId(), "companyId");
            AccountRow account = repository.findAccount(uuid(request.getAccountId(), "accountId"))
                    .orElseThrow(() -> new NoSuchElementException("Счёт не найден"));
            if (!account.companyId().equals(companyId)) {
                throw new IllegalArgumentException("Счёт не принадлежит указанной компании");
            }
            BigDecimal projected = account.balance()
                    .add(decimal(request.getProjectedNetFlow()))
                    .subtract(decimal(request.getCommittedOutflows()))
                    .subtract(decimal(request.getAmount()))
                    .setScale(2, RoundingMode.HALF_UP);
            boolean sufficient = projected.signum() >= 0;
            return ProjectedBalanceResponse.newBuilder()
                    .setSufficient(sufficient)
                    .setAccountBalance(account.balance().toPlainString())
                    .setProjectedBalance(projected.toPlainString())
                    .setMessage(sufficient ? "Прогнозного остатка достаточно"
                            : "Прогнозный остаток после платежа отрицательный: " + projected.toPlainString())
                    .build();
        });
    }

    private <T> void handle(String operation, StreamObserver<T> out, Body<T> body) {
        String cid = CorrelationServerInterceptor.currentId();
        try {
            T response = body.run();
            logger.info("[corr=" + cid + "] " + operation + " выполнен");
            out.onNext(response);
            out.onCompleted();
        } catch (NoSuchElementException e) {
            out.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException e) {
            logger.warning("[corr=" + cid + "] " + operation + ": " + e.getMessage());
            out.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                out.onError(Status.ALREADY_EXISTS.withDescription("Компания с таким УНП уже существует")
                        .asRuntimeException());
            } else if ("23503".equals(e.getSQLState())) {
                out.onError(Status.FAILED_PRECONDITION
                        .withDescription("Нарушена связь данных: есть зависимые записи или не найден родитель")
                        .asRuntimeException());
            } else {
                logger.log(Level.SEVERE, "[corr=" + cid + "] " + operation + ": ошибка БД", e);
                out.onError(Status.UNAVAILABLE.withDescription("Company Service: ошибка базы данных")
                        .asRuntimeException());
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[corr=" + cid + "] " + operation + ": непредвиденная ошибка", e);
            out.onError(Status.INTERNAL.withDescription("Внутренняя ошибка Company Service").asRuntimeException());
        }
    }

    private static UUID uuid(String raw, String field) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Некорректный UUID в поле " + field);
        }
    }

    private static BigDecimal decimal(String raw) {
        return raw == null || raw.isBlank() ? BigDecimal.ZERO : new BigDecimal(raw.trim());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static CompanyData toProto(CompanyRow c) {
        return CompanyData.newBuilder().setId(c.id().toString()).setName(nz(c.name())).setTaxId(nz(c.taxId()))
                .setAddress(nz(c.address())).setPhone(nz(c.phone())).setOwnerId(c.ownerId().toString())
                .setDescription(nz(c.description())).setWebsiteUrl(nz(c.websiteUrl()))
                .setLogoUrl(nz(c.logoUrl())).setStatus(nz(c.status())).build();
    }

    private static AccountData toProto(AccountRow a) {
        return AccountData.newBuilder().setId(a.id().toString()).setCompanyId(a.companyId().toString())
                .setAccountName(a.accountName()).setAccountType(a.accountType()).setCurrency(a.currency())
                .setBalance(a.balance().toPlainString()).setIsActive(a.active()).build();
    }
}
