package com.finance.graphqlapi.config;

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
        GraphQLScalarType uuid = GraphQLScalarType.newScalar().name("UUID").description("UUID scalar")
                .coercing(new Coercing<UUID, String>() {
                    public String serialize(Object input) { try { return input instanceof UUID u ? u.toString() : UUID.fromString(input.toString().replace("\"", "")).toString(); } catch(Exception e){throw new CoercingSerializeException("Invalid UUID");} }
                    public UUID parseValue(Object input) { try { return UUID.fromString(input.toString().replace("\"", "")); } catch(Exception e){throw new CoercingParseValueException("Invalid UUID");} }
                    public UUID parseLiteral(Object input) { try { return UUID.fromString(input.toString().replace("\"", "")); } catch(Exception e){throw new CoercingParseLiteralException("Invalid UUID");} }
                }).build();
        GraphQLScalarType date = GraphQLScalarType.newScalar().name("LocalDate").description("ISO-8601 date")
                .coercing(new Coercing<LocalDate, String>() {
                    public String serialize(Object input){return input.toString();}
                    public LocalDate parseValue(Object input){return LocalDate.parse(input.toString().replace("\"", ""));}
                    public LocalDate parseLiteral(Object input){return LocalDate.parse(input.toString().replace("\"", ""));}
                }).build();
        GraphQLScalarType dateTime = GraphQLScalarType.newScalar().name("LocalDateTime").description("ISO-8601 date-time")
                .coercing(new Coercing<LocalDateTime, String>() {
                    public String serialize(Object input){return input.toString();}
                    public LocalDateTime parseValue(Object input){return LocalDateTime.parse(input.toString().replace("\"", ""));}
                    public LocalDateTime parseLiteral(Object input){return LocalDateTime.parse(input.toString().replace("\"", ""));}
                }).build();
        return wiring -> wiring.scalar(uuid).scalar(date).scalar(dateTime);
    }
}
