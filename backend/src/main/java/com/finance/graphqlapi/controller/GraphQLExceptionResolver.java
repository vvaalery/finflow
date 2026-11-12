
package com.finance.graphqlapi.controller;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

@Component
public class GraphQLExceptionResolver extends DataFetcherExceptionResolverAdapter {

    private static final Logger logger =
            LoggerFactory.getLogger(GraphQLExceptionResolver.class);

    @Override
    protected GraphQLError resolveToSingleError(
            Throwable ex,
            DataFetchingEnvironment env
    ) {
        String operation = env.getField().getName();

        if (ex instanceof NoSuchElementException
                || ex instanceof IllegalArgumentException) {
            logger.warn(
                    "GraphQL validation error: operation={}, message={}",
                    operation,
                    ex.getMessage()
            );

            return GraphqlErrorBuilder.newError(env)
                    .message(ex.getMessage())
                    .build();
        }

        logger.error(
                "GraphQL internal error: operation={}",
                operation,
                ex
        );

        return GraphqlErrorBuilder.newError(env)
                .message("Внутренняя ошибка сервера")
                .build();
    }
}
