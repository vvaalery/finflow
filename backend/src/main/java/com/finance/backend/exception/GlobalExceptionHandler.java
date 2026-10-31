package com.finance.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<?> notFound(NoSuchElementException e){return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("timestamp", Instant.now(),"error","NOT_FOUND","message",e.getMessage()));}
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<?> badRequest(Exception e){return ResponseEntity.badRequest().body(Map.of("timestamp",Instant.now(),"error","BAD_REQUEST","message",e.getMessage()));}
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<?> unauthorized(Exception e){return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("timestamp",Instant.now(),"error","UNAUTHORIZED","message","Неверный email или пароль"));}
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> internal(Exception e){return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("timestamp",Instant.now(),"error","INTERNAL_ERROR","message",e.getMessage()==null?"Внутренняя ошибка":e.getMessage()));}
}
