package com.finance.company;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.util.concurrent.TimeUnit;

public class CompanyServiceApplication {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(
                System.getenv().getOrDefault("GRPC_PORT", "9090")
        );

        Server server = ServerBuilder.forPort(port)
                .addService(new CompanyGrpcService())
                .build()
                .start();

        System.out.println("Company Service gRPC started on port " + port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Stopping Company Service");
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
}
