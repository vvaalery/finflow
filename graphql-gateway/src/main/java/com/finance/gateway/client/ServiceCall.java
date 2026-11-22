package com.finance.gateway.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

final class ServiceCall {
    private ServiceCall() {}

    static <T> T run(ObjectMapper mapper, Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientResponseException e) {
            throw translate(mapper, e);
        } catch (ResourceAccessException e) {
            throw new ServiceUnavailableException("Внутренний сервис временно недоступен");
        }
    }

    private static RuntimeException translate(ObjectMapper mapper, RestClientResponseException e) {
        String message = message(mapper, e);
        return switch (e.getStatusCode().value()) {
            case 400, 409, 422 -> new IllegalArgumentException(message);
            case 401 -> new BadCredentialsException(message);
            case 403 -> new AccessDeniedException(message);
            case 404 -> new NoSuchElementException(message);
            default -> new ServiceUnavailableException("Внутренняя ошибка сервиса");
        };
    }

    private static String message(ObjectMapper mapper, RestClientResponseException e) {
        try {
            JsonNode node = mapper.readTree(e.getResponseBodyAsString());
            return node.hasNonNull("message") ? node.get("message").asText() : "Ошибка запроса";
        } catch (Exception ex) {
            return "Ошибка запроса";
        }
    }
}
