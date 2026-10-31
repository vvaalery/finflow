
package com.finance.graphqlapi.controller;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

@Component
public class GraphQLExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(
            Throwable ex,
            DataFetchingEnvironment env) {

        Throwable cause = ex;

        // Извлекаем исходную причину, если исключение обёрнуто.
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }

        String message;

        if (cause instanceof AccessDeniedException
                || cause instanceof AuthorizationDeniedException) {
            message = "Недостаточно прав для выполнения операции";
        } else if (cause instanceof NoSuchElementException
                || cause instanceof IllegalArgumentException) {
            message = cause.getMessage();
        } else {
            message = "Внутренняя ошибка сервера";
        }

        return GraphqlErrorBuilder.newError(env)
                .message(message)
                .build();
    }
}
