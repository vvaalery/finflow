package com.finance.gateway.logging;

import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class Correlation {
    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private Correlation() {}

    public static String current() {
        String id = MDC.get(MDC_KEY);
        if (id != null) {
            return id;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getDetails() instanceof String s ? s : null;
    }
}
