package com.finance.graphqlapi.controller;

import com.finance.gateway.client.ServiceUnavailableException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GraphQLExceptionResolver extends DataFetcherExceptionResolverAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GraphQLExceptionResolver.class);

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        String operation = env.getField().getName();

        if (ex instanceof AccessDeniedException) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean anonymous = auth == null || auth instanceof AnonymousAuthenticationToken;
            if (anonymous) return error(env, ErrorType.UNAUTHORIZED, "Требуется аутентификация");
            String message = ex instanceof AuthorizationDeniedException ? "Доступ запрещён: недостаточно прав для операции" : ex.getMessage();
            return error(env, ErrorType.FORBIDDEN, message);
        }
        if (ex instanceof AuthenticationException) {
            return error(env, ErrorType.UNAUTHORIZED, ex.getMessage());
        }
        if (ex instanceof NoSuchElementException) {
            return error(env, ErrorType.NOT_FOUND, ex.getMessage());
        }
        if (ex instanceof IllegalArgumentException) {
            logger.warn("GraphQL validation error: operation={}, message={}", operation, ex.getMessage());
            return error(env, ErrorType.BAD_REQUEST, ex.getMessage());
        }
        if (ex instanceof ConstraintViolationException cve) {
            return error(env, ErrorType.BAD_REQUEST,
                    cve.getConstraintViolations().stream().map(v -> v.getMessage()).collect(Collectors.joining("; ")));
        }
        if (ex instanceof BindException be) {
            var fe = be.getFieldError();
            return error(env, ErrorType.BAD_REQUEST, fe == null ? "Некорректные данные" : fe.getDefaultMessage());
        }
        if (ex instanceof ServiceUnavailableException) {
            return error(env, ErrorType.INTERNAL_ERROR, ex.getMessage());
        }

        logger.error("GraphQL internal error: operation={}", operation, ex);
        return error(env, ErrorType.INTERNAL_ERROR, "Внутренняя ошибка сервера");
    }

    private GraphQLError error(DataFetchingEnvironment env, ErrorType type, String message) {
        return GraphqlErrorBuilder.newError(env).errorType(type).message(message == null ? "Ошибка" : message).build();
    }
}
