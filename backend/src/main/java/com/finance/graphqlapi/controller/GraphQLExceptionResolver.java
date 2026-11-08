package com.finance.graphqlapi.controller;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

@Component
public class GraphQLExceptionResolver extends DataFetcherExceptionResolverAdapter {
    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        String message = ex instanceof NoSuchElementException || ex instanceof IllegalArgumentException ? ex.getMessage() : "Внутренняя ошибка сервера";
        return GraphqlErrorBuilder.newError(env).message(message).build();
    }
}
