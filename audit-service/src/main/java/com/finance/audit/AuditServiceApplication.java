package com.finance.audit;

import java.util.concurrent.CountDownLatch;

public class AuditServiceApplication {

    public static void main(String[] args) throws Exception {
        String kafka = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");

        AuditStore store = new AuditStore();
        initWithRetry(store);

        AuditEventConsumer consumer = new AuditEventConsumer(store, kafka);
        Thread thread = new Thread(consumer, "audit-event-consumer");
        thread.start();

        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Stopping Audit Service");
            consumer.stop();
            stopped.countDown();
        }));

        System.out.println("Audit Service started");
        stopped.await();
    }

    private static void initWithRetry(AuditStore store) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 30; attempt++) {
            try {
                store.init();
                return;
            } catch (Exception e) {
                last = e;
                System.out.println("База audit_db пока недоступна (попытка " + attempt + "): " + e.getMessage());
                Thread.sleep(2000);
            }
        }
        throw last;
    }
}
