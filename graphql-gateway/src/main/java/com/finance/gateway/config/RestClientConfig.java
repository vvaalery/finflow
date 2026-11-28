package com.finance.gateway.config;

import com.finance.gateway.logging.Correlation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class RestClientConfig {

    @Bean
    RestClientCustomizer timeoutsAndCorrelation(
            @Value("${services.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${services.read-timeout-ms:15000}") int readTimeoutMs) {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(connectTimeoutMs);
            factory.setReadTimeout(readTimeoutMs);
            builder.requestFactory(factory).requestInterceptor((request, body, execution) -> {
                String id = Correlation.current();
                if (id != null) {
                    request.getHeaders().set(Correlation.HEADER, id);
                }
                return execution.execute(request, body);
            });
        };
    }
}
