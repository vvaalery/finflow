package com.finance.company;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.util.concurrent.TimeUnit;

public class CompanyServiceApplication {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("GRPC_PORT", "9090"));
        String kafka = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String topic = System.getenv().getOrDefault("ACCOUNT_SNAPSHOT_TOPIC", "finflow.accounts.snapshot");
        boolean bootstrapEnabled = Boolean.parseBoolean(System.getenv().getOrDefault("ACCOUNT_BOOTSTRAP_ENABLED", "true"));

        DatabaseConfig database = new DatabaseConfig();
        CompanyRepository repository = new CompanyRepository(database);
        initWithRetry(repository);

        AccountSnapshotConsumer consumer = null;
        if (bootstrapEnabled) {
            consumer = new AccountSnapshotConsumer(repository, kafka, topic);
            Thread consumerThread = new Thread(consumer, "account-bootstrap-consumer");
            consumerThread.setDaemon(true);
            consumerThread.start();
        }
        final AccountSnapshotConsumer runningConsumer = consumer;

        Server server = ServerBuilder.forPort(port)
                .intercept(new CorrelationServerInterceptor())
                .addService(new CompanyGrpcService(repository))
                .build()
                .start();

        System.out.println("Company Service gRPC started on port " + port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Stopping Company Service");
            if (runningConsumer != null) {
                runningConsumer.stop();
            }
            server.shutdown();
            try {
                if (!server.awaitTermination(10, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                server.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));

        server.awaitTermination();
    }

    private static void initWithRetry(CompanyRepository repository) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 30; attempt++) {
            try {
                repository.init();
                return;
            } catch (Exception e) {
                last = e;
                System.out.println("База company_db пока недоступна (попытка " + attempt + "): " + e.getMessage());
                Thread.sleep(2000);
            }
        }
        throw last;
    }
}
