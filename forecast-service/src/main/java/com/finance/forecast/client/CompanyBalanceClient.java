package com.finance.forecast.client;

import com.finance.company.grpc.CompanyServiceGrpc;
import com.finance.company.grpc.ForecastInputRequest;
import com.finance.company.grpc.ForecastInputResponse;
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
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class CompanyBalanceClient {
    private static final Logger log = LoggerFactory.getLogger(CompanyBalanceClient.class);

    private static final Metadata.Key<String> CORRELATION =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    private final ManagedChannel channel;
    private final CompanyServiceGrpc.CompanyServiceBlockingStub stub;
    private final long timeoutMs;
    private final int maxAttempts;

    public CompanyBalanceClient(
            @Value("${company-service.host}") String host,
            @Value("${company-service.port}") int port,
            @Value("${company-service.timeout-ms}") long timeoutMs,
            @Value("${company-service.max-attempts}") int maxAttempts
    ) {
        this.channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .intercept(new ClientInterceptor() {
                    @Override
                    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                            MethodDescriptor<ReqT, RespT> method,
                            CallOptions options,
                            Channel next
                    ) {
                        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                                next.newCall(method, options)
                        ) {
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

    public BigDecimal baseCurrencyBalance(UUID companyId) {
        for (int attempt = 1; ; attempt++) {
            try {
                ForecastInputResponse response = stub
                        .withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                        .getForecastInput(
                                ForecastInputRequest.newBuilder()
                                        .setCompanyId(companyId.toString())
                                        .build()
                        );

                return new BigDecimal(response.getBaseCurrencyBalance());
            } catch (StatusRuntimeException e) {
                Status.Code code = e.getStatus().getCode();

                boolean retryable = code == Status.Code.UNAVAILABLE
                        || code == Status.Code.DEADLINE_EXCEEDED;

                log.warn(
                        "Company Service GetForecastInput: {} (попытка {}/{})",
                        code,
                        attempt,
                        maxAttempts
                );

                if (!retryable || attempt >= maxAttempts) {
                    throw new IllegalStateException("Company Service недоступен", e);
                }

                try {
                    Thread.sleep(200L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Ожидание Company Service прервано",
                            interrupted
                    );
                }
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        channel.shutdown();
    }
}
