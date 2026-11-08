
package com.finance.graphqlapi.config;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Configuration
public class GraphQLScalarConfig {
    @Bean
    RuntimeWiringConfigurer runtimeWiringConfigurer() {
        GraphQLScalarType uuid = GraphQLScalarType.newScalar()
                .name("UUID")
                .description("UUID scalar")
                .coercing(new Coercing<UUID, String>() {
                    @Override
                    public String serialize(Object input) {
                        try {
                            return UUID.fromString(input.toString()).toString();
                        } catch (Exception e) {
                            throw new CoercingSerializeException("Invalid UUID");
                        }
                    }

                    @Override
                    public UUID parseValue(Object input) {
                        try {
                            return UUID.fromString(input.toString());
                        } catch (Exception e) {
                            throw new CoercingParseValueException("Invalid UUID");
                        }
                    }

                    @Override
                    public UUID parseLiteral(Object input) {
                        if (input instanceof StringValue value) {
                            try {
                                return UUID.fromString(value.getValue());
                            } catch (Exception e) {
                                throw new CoercingParseLiteralException("Invalid UUID");
                            }
                        }
                        throw new CoercingParseLiteralException("Invalid UUID");
                    }
                })
                .build();

        GraphQLScalarType date = GraphQLScalarType.newScalar()
                .name("LocalDate")
                .description("ISO-8601 date")
                .coercing(new Coercing<LocalDate, String>() {
                    @Override
                    public String serialize(Object input) {
                        return input.toString();
                    }

                    @Override
                    public LocalDate parseValue(Object input) {
                        return LocalDate.parse(input.toString());
                    }

                    @Override
                    public LocalDate parseLiteral(Object input) {
                        if (input instanceof StringValue value) {
                            return LocalDate.parse(value.getValue());
                        }
                        throw new CoercingParseLiteralException("Invalid LocalDate");
                    }
                })
                .build();

        GraphQLScalarType dateTime = GraphQLScalarType.newScalar()
                .name("LocalDateTime")
                .description("ISO-8601 date-time")
                .coercing(new Coercing<LocalDateTime, String>() {
                    @Override
                    public String serialize(Object input) {
                        return input.toString();
                    }

                    @Override
                    public LocalDateTime parseValue(Object input) {
                        return LocalDateTime.parse(input.toString());
                    }

                    @Override
                    public LocalDateTime parseLiteral(Object input) {
                        if (input instanceof StringValue value) {
                            return LocalDateTime.parse(value.getValue());
                        }
                        throw new CoercingParseLiteralException("Invalid LocalDateTime");
                    }
                })
                .build();

        return wiring -> wiring.scalar(uuid).scalar(date).scalar(dateTime);
    }
}
