package com.finance.backend.config;

import com.finance.backend.logging.CorrelationFilter;
import org.slf4j.MDC;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestClientConfig {

    @Bean
    RestClientCustomizer correlationCustomizer() {
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            String id = MDC.get(CorrelationFilter.MDC_KEY);
            if (id != null) {
                request.getHeaders().set(CorrelationFilter.HEADER, id);
            }
            return execution.execute(request, body);
        });
    }
}
