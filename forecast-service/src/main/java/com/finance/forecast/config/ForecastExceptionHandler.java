package com.finance.forecast.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ForecastExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ForecastExceptionHandler.class);

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<?> notFound(NoSuchElementException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> badRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException e) {
        return body(e.getStatusCode(), e.getReason());
    }

    @ExceptionHandler({ResourceAccessException.class, IllegalStateException.class})
    ResponseEntity<?> unavailable(RuntimeException e) {
        log.warn("Зависимый сервис недоступен: {}", e.getMessage());
        return body(HttpStatus.SERVICE_UNAVAILABLE, "Зависимый сервис временно недоступен");
    }

    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<?> upstream(RestClientResponseException e) {
        log.warn("Finance Service вернул {}: {}", e.getStatusCode(), e.getMessage());
        int code = e.getStatusCode().value();
        if (code == 403 || code == 404 || code == 401) {
            return body(e.getStatusCode(), code == 404 ? "Компания не найдена" : "Нет доступа");
        }
        return body(HttpStatus.BAD_GATEWAY, "Finance Service вернул ошибку");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> internal(Exception e) {
        log.error("Внутренняя ошибка Forecast Service", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка Forecast Service");
    }

    private ResponseEntity<?> body(HttpStatusCode status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "error", String.valueOf(status.value()),
                "message", message == null ? "Ошибка" : message
        ));
    }
}
