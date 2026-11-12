package com.finance.paymentservice.client;

import com.finance.company.grpc.AccountOwnershipRequest;
import com.finance.company.grpc.AccountOwnershipResponse;
import com.finance.company.grpc.CompanyServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
@Profile("!mock")
public class CompanyServiceGatewayGrpc implements CompanyServiceGateway {

    private static final Logger logger =
            Logger.getLogger(CompanyServiceGatewayGrpc.class.getName());

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
        this.channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();

        this.stub = CompanyServiceGrpc.newBlockingStub(channel);
        this.timeoutMs = timeoutMs;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public boolean accountBelongsToCompany(UUID accountId, UUID companyId) {
        AccountOwnershipRequest request = AccountOwnershipRequest.newBuilder()
                .setAccountId(accountId.toString())
                .setCompanyId(companyId.toString())
                .build();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                AccountOwnershipResponse response = stub
                        .withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                        .validateAccountOwnership(request);

                logger.info("Company Service response: attempt=" + attempt
                        + ", accountId=" + accountId
                        + ", belongs=" + response.getBelongsToCompany());

                return response.getAccountExists()
                        && response.getBelongsToCompany();

            } catch (StatusRuntimeException e) {
                Status.Code code = e.getStatus().getCode();

                logger.log(Level.WARNING,
                        "Company Service gRPC error: attempt=" + attempt
                                + ", status=" + code,
                        e);

                if (code != Status.Code.UNAVAILABLE
                        && code != Status.Code.DEADLINE_EXCEEDED) {
                    throw new IllegalStateException(
                            "Ошибка проверки счёта в Company Service", e);
                }

                if (attempt == maxAttempts) {
                    throw new IllegalStateException(
                            "Сервис проверки счетов временно недоступен", e);
                }

                try {
                    Thread.sleep(200L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Ожидание ответа Company Service прервано",
                            interrupted);
                }
            }
        }

        throw new IllegalStateException(
                "Не удалось выполнить проверку счёта");
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
