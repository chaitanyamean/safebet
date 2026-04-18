package com.entain.betsafe.gateway.dto;

import java.util.List;

/**
 * Structured error response — used for all error HTTP responses.
 */
public record ErrorResponse(
    String error,
    String requestId,
    String tenantId,
    Long retryAfterMs,
    List<FieldError> errors
) {

  public record FieldError(String field, String message) {
  }

  public static ErrorResponse validation(String requestId, List<FieldError> errors) {
    return new ErrorResponse("VALIDATION_ERROR", requestId, null, null, errors);
  }

  public static ErrorResponse rateLimited(String requestId, String tenantId, long retryAfterMs) {
    return new ErrorResponse("RATE_LIMIT_EXCEEDED", requestId, tenantId, retryAfterMs, null);
  }

  public static ErrorResponse unavailable(String requestId) {
    return new ErrorResponse("UPSTREAM_UNAVAILABLE", requestId, null, null, null);
  }

  public static ErrorResponse internal(String requestId) {
    return new ErrorResponse("INTERNAL_ERROR", requestId, null, null, null);
  }
}
