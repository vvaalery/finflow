package com.finance.gateway.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GraphQlLoggingInterceptor implements WebGraphQlInterceptor {
    private static final Logger log = LoggerFactory.getLogger(GraphQlLoggingInterceptor.class);

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        String correlationId = Correlation.current();
        if (correlationId == null) {
            correlationId = request.getHeaders().getFirst(Correlation.HEADER);
        }
        final String cid = correlationId;
        final long started = System.nanoTime();
        log.info("[corr={}] GraphQL запрос: operation={}, requestId={}", cid, request.getOperationName(), request.getId());
        return chain.next(request).doOnNext(response -> log.info(
                "[corr={}] GraphQL ответ: operation={}, ошибок={}, {} мс", cid, request.getOperationName(),
                response.getErrors().size(), (System.nanoTime() - started) / 1_000_000));
    }
}
