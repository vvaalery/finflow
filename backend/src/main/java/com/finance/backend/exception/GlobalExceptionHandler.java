package com.finance.backend.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<?> notFound(NoSuchElementException e){return body(HttpStatus.NOT_FOUND,"NOT_FOUND",e.getMessage());}

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> badRequest(IllegalArgumentException e){return body(HttpStatus.BAD_REQUEST,"BAD_REQUEST",e.getMessage());}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> invalid(MethodArgumentNotValidException e){
        var fe = e.getBindingResult().getFieldError();
        return body(HttpStatus.BAD_REQUEST,"BAD_REQUEST", fe == null ? "Некорректные данные" : fe.getDefaultMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<?> constraint(ConstraintViolationException e){return body(HttpStatus.BAD_REQUEST,"BAD_REQUEST",e.getMessage());}

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> forbidden(AccessDeniedException e){return body(HttpStatus.FORBIDDEN,"FORBIDDEN",e.getMessage()==null?"Доступ запрещён":e.getMessage());}

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<?> badCredentials(Exception e){return body(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Неверный email или пароль");}

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<?> unauthorized(AuthenticationException e){return body(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Требуется аутентификация");}

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> internal(Exception e){return body(HttpStatus.INTERNAL_SERVER_ERROR,"INTERNAL_ERROR",e.getMessage()==null?"Внутренняя ошибка":e.getMessage());}

    private ResponseEntity<?> body(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(Map.of("timestamp", Instant.now().toString(), "error", error,
                "message", message == null ? "Ошибка" : message));
    }
}