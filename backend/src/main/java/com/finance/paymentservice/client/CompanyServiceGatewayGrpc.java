package com.finance.paymentservice.client;

import com.finance.company.grpc.*;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class CompanyServiceGatewayGrpc implements CompanyServiceGateway {

    private static final Logger log = LoggerFactory.getLogger(CompanyServiceGatewayGrpc.class);
    private static final Metadata.Key<String> CORRELATION =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    private final ManagedChannel channel;
    private final CompanyServiceGrpc.CompanyServiceBlockingStub stub;
    private final long timeoutMs;
    private final int maxAttempts;

    public CompanyServiceGatewayGrpc(
            @Value("${COMPANY_SERVICE_HOST:company-service}") String host,
            @Value("${COMPANY_SERVICE_PORT:9090}") int port,
            @Value("${COMPANY_SERVICE_TIMEOUT_MS:2000}") long timeoutMs,
            @Value("${COMPANY_SERVICE_MAX_ATTEMPTS:3}") int maxAttempts
    ) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .intercept(new ClientInterceptor() {
                    @Override
                    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                            MethodDescriptor<ReqT, RespT> method, CallOptions options, Channel next) {
                        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                                next.newCall(method, options)) {
                            @Override
                            public void start(Listener<RespT> listener, Metadata headers) {
                                String id = MDC.get("correlationId");
                                if (id != null) {
                                    headers.put(CORRELATION, id);
                                }
                                super.start(listener, headers);
                            }
                        };
                    }
                })
                .build();
        this.stub = CompanyServiceGrpc.newBlockingStub(channel);
        this.timeoutMs = timeoutMs;
        this.maxAttempts = maxAttempts;
    }

    private CompanyServiceGrpc.CompanyServiceBlockingStub s() {
        return stub.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean accountBelongsToCompany(UUID accountId, UUID companyId) {
        AccountOwnershipResponse r = call("ValidateAccountOwnership", () -> s().validateAccountOwnership(
                AccountOwnershipRequest.newBuilder()
                        .setAccountId(accountId.toString()).setCompanyId(companyId.toString()).build()));
        return r.getAccountExists() && r.getBelongsToCompany();
    }

    @Override
    public Optional<CompanyDto> getCompany(UUID id) {
        GetCompanyResponse r = call("GetCompany", () -> s().getCompany(
                GetCompanyRequest.newBuilder().setCompanyId(id.toString()).build()));
        return r.getFound() ? Optional.of(fromProto(r.getCompany())) : Optional.empty();
    }

    @Override
    public Optional<AccountDto> getAccount(UUID id) {
        GetAccountResponse r = call("GetAccount", () -> s().getAccount(
                GetAccountRequest.newBuilder().setAccountId(id.toString()).build()));
        return r.getFound() ? Optional.of(fromProto(r.getAccount())) : Optional.empty();
    }

    @Override
    public CompanyDto saveCompany(CompanyDto c) {
        CompanyData d = CompanyData.newBuilder()
                .setId(c.id().toString()).setName(nz(c.name())).setTaxId(nz(c.taxId())).setAddress(nz(c.address()))
                .setPhone(nz(c.phone())).setOwnerId(c.ownerId().toString()).setDescription(nz(c.description()))
                .setWebsiteUrl(nz(c.websiteUrl())).setLogoUrl(nz(c.logoUrl())).setStatus(nz(c.status())).build();
        return fromProto(call("SaveCompany", () -> s().saveCompany(SaveCompanyRequest.newBuilder().setCompany(d).build())));
    }

    @Override
    public void deleteCompany(UUID id) {
        call("DeleteCompany", () -> s().deleteCompany(DeleteRequest.newBuilder().setId(id.toString()).build()));
    }

    @Override
    public AccountDto saveAccount(AccountDto a) {
        AccountData d = AccountData.newBuilder()
                .setId(a.id().toString()).setCompanyId(a.companyId().toString()).setAccountName(nz(a.accountName()))
                .setAccountType(nz(a.accountType())).setCurrency(nz(a.currency()))
                .setBalance(a.balance() == null ? "0" : a.balance().toPlainString()).setIsActive(a.active()).build();
        return fromProto(call("SaveAccount", () -> s().saveAccount(SaveAccountRequest.newBuilder().setAccount(d).build())));
    }

    @Override
    public void deleteAccount(UUID id) {
        call("DeleteAccount", () -> s().deleteAccount(DeleteRequest.newBuilder().setId(id.toString()).build()));
    }

    @Override
    public BigDecimal changeBalance(UUID operationId, UUID accountId, BigDecimal delta) {
        ChangeBalanceResponse r = call("ChangeBalance", () -> s().changeBalance(ChangeBalanceRequest.newBuilder()
                .setOperationId(operationId.toString()).setAccountId(accountId.toString())
                .setDelta(delta.toPlainString()).build()));
        return new BigDecimal(r.getBalance());
    }

    @Override
    public ForecastInputDto forecastInput(UUID companyId) {
        ForecastInputResponse r = call("GetForecastInput", () -> s().getForecastInput(
                ForecastInputRequest.newBuilder().setCompanyId(companyId.toString()).build()));
        return new ForecastInputDto(new BigDecimal(r.getBaseCurrencyBalance()),
                r.getAccountsList().stream().map(CompanyServiceGatewayGrpc::fromProto).toList());
    }

    @Override
    public BalanceCheckDto checkProjectedBalance(UUID companyId, UUID accountId, BigDecimal amount,
                                                 BigDecimal committedOutflows, BigDecimal projectedNetFlow) {
        ProjectedBalanceResponse r = call("CheckProjectedBalance", () -> s().checkProjectedBalance(
                ProjectedBalanceRequest.newBuilder()
                        .setCompanyId(companyId.toString()).setAccountId(accountId.toString())
                        .setAmount(amount.toPlainString())
                        .setCommittedOutflows(committedOutflows.toPlainString())
                        .setProjectedNetFlow(projectedNetFlow.toPlainString()).build()));
        return new BalanceCheckDto(r.getSufficient(), new BigDecimal(r.getAccountBalance()),
                new BigDecimal(r.getProjectedBalance()), r.getMessage());
    }

    private <T> T call(String operation, Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                T result = action.get();
                log.debug("Company Service {}: успех, попытка {}", operation, attempt);
                return result;
            } catch (StatusRuntimeException e) {
                Status.Code code = e.getStatus().getCode();
                String description = e.getStatus().getDescription();
                switch (code) {
                    case NOT_FOUND -> throw new NoSuchElementException(description);
                    case INVALID_ARGUMENT, ALREADY_EXISTS, FAILED_PRECONDITION ->
                            throw new IllegalArgumentException(description);
                    case UNAVAILABLE, DEADLINE_EXCEEDED -> {
                        log.warn("Company Service {}: {} (попытка {}/{})", operation, code, attempt, maxAttempts);
                        if (attempt >= maxAttempts) {
                            throw new IllegalStateException("Сервис компаний и счетов временно недоступен", e);
                        }
                        pause(200L * attempt);
                    }
                    default -> throw new IllegalStateException("Ошибка Company Service: " + code, e);
                }
            }
        }
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание ответа Company Service прервано", e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static CompanyDto fromProto(CompanyData d) {
        return new CompanyDto(UUID.fromString(d.getId()), d.getName(), blankToNull(d.getTaxId()),
                blankToNull(d.getAddress()), blankToNull(d.getPhone()), UUID.fromString(d.getOwnerId()),
                blankToNull(d.getDescription()), blankToNull(d.getWebsiteUrl()), blankToNull(d.getLogoUrl()),
                d.getStatus());
    }

    private static AccountDto fromProto(AccountData d) {
        return new AccountDto(UUID.fromString(d.getId()), UUID.fromString(d.getCompanyId()), d.getAccountName(),
                d.getAccountType(), d.getCurrency(), new BigDecimal(d.getBalance()), d.getIsActive());
    }

    @PreDestroy
    public void shutdown() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
