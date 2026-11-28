package com.finance.kafka.event;

import java.util.List;
import java.util.Locale;

public final class EventTypes {
    public static final String USER_CREATED = "USER_CREATED";
    public static final String PAYMENT_REQUEST_CREATED = "PAYMENT_REQUEST_CREATED";
    public static final String PAYMENT_REQUEST_APPROVED = "PAYMENT_REQUEST_APPROVED";
    public static final String PAYMENT_REQUEST_REJECTED = "PAYMENT_REQUEST_REJECTED";
    public static final String PAYMENT_CALENDAR_CREATED = "PAYMENT_CALENDAR_CREATED";
    public static final String TRANSACTION_CREATED = "TRANSACTION_CREATED";
    public static final String TRANSACTION_UPDATED = "TRANSACTION_UPDATED";
    public static final String TRANSACTION_DELETED = "TRANSACTION_DELETED";
    public static final String TRANSACTIONS_IMPORTED = "TRANSACTIONS_IMPORTED";
    public static final String FORECAST_GENERATED = "FORECAST_GENERATED";

    public static final String TOPIC_TRANSACTION_CREATED = "finflow.transaction-created";
    public static final String TOPIC_TRANSACTION_UPDATED = "finflow.transaction-updated";
    public static final String TOPIC_TRANSACTION_DELETED = "finflow.transaction-deleted";
    public static final String TOPIC_TRANSACTIONS_IMPORTED = "finflow.transactions-imported";
    public static final String TOPIC_PAYMENT_REQUEST_APPROVED = "finflow.payment-request-approved";

    public static final List<String> ALL = List.of(
            USER_CREATED, PAYMENT_REQUEST_CREATED, PAYMENT_REQUEST_APPROVED,
            PAYMENT_REQUEST_REJECTED, PAYMENT_CALENDAR_CREATED,
            TRANSACTION_CREATED, TRANSACTION_UPDATED, TRANSACTION_DELETED,
            TRANSACTIONS_IMPORTED, FORECAST_GENERATED
    );

    private EventTypes() {
    }

    public static String topic(String type) {
        return "finflow." + type.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static String dlt(String type) {
        return topic(type) + ".DLT";
    }

    public static String[] dltTopics() {
        return ALL.stream().map(EventTypes::dlt).toArray(String[]::new);
    }
}
