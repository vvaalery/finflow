package com.finance.company;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.util.concurrent.TimeUnit;

public class CompanyServiceApplication {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("GRPC_PORT", "9090"));
        String kafka = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String topic = System.getenv().getOrDefault("ACCOUNT_SNAPSHOT_TOPIC", "finflow.accounts.snapshot");

        DatabaseConfig database = new DatabaseConfig();
        AccountReplicaStore store = new AccountReplicaStore(database);
        initWithRetry(store);

        AccountSnapshotConsumer consumer = new AccountSnapshotConsumer(store, kafka, topic);
        Thread consumerThread = new Thread(consumer, "account-snapshot-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();

        Server server = ServerBuilder.forPort(port)
                .addService(new CompanyGrpcService(database))
                .build()
                .start();

        System.out.println("Company Service gRPC started on port " + port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Stopping Company Service");
            consumer.stop();
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

    private static void initWithRetry(AccountReplicaStore store) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 30; attempt++) {
            try {
                store.init();
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
