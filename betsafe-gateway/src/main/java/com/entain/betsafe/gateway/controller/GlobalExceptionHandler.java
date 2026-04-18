package com.entain.betsafe.gateway.controller;

import com.entain.betsafe.gateway.dto.ErrorResponse;
import com.entain.betsafe.gateway.dto.ErrorResponse.FieldError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

/**
 * Maps all exception types to structured JSON responses — no unhandled 500s with stack traces.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    String requestId = UUID.randomUUID().toString();
    List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
        .toList();
    return ResponseEntity.badRequest()
        .header("X-Request-Id", requestId)
        .body(ErrorResponse.validation(requestId, errors));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
    String requestId = UUID.randomUUID().toString();
    List<FieldError> errors = List.of(
        new FieldError("body", "Malformed JSON request body"));
    return ResponseEntity.badRequest()
        .header("X-Request-Id", requestId)
        .body(ErrorResponse.validation(requestId, errors));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
    String requestId = UUID.randomUUID().toString();
    log.error("Unhandled exception: request_id={}", requestId, ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .header("X-Request-Id", requestId)
        .body(ErrorResponse.internal(requestId));
  }
}
