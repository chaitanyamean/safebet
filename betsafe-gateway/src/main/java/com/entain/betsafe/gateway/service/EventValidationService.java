package com.entain.betsafe.gateway.service;

import com.entain.betsafe.gateway.dto.ErrorResponse.FieldError;
import com.entain.betsafe.gateway.dto.EventRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Validates inbound event requests against schema and business rules.
 */
@Service
public class EventValidationService {

  private static final Set<String> VALID_EVENT_TYPES =
      Set.of("BET", "DEPOSIT", "SESSION", "GAME_RESULT");
  private static final Duration MAX_CLOCK_SKEW = Duration.ofHours(48);

  public List<FieldError> validate(EventRequest request) {
    List<FieldError> errors = new ArrayList<>();

    validateEventId(request.eventId(), errors);
    validateEventType(request.eventType(), errors);
    validateClientTimestamp(request.clientTimestamp(), errors);

    return errors;
  }

  private void validateEventId(String eventId, List<FieldError> errors) {
    if (eventId == null || eventId.isBlank()) {
      return; // handled by @NotBlank
    }
    try {
      UUID parsed = UUID.fromString(eventId);
      // Verify it's a valid UUID v4 format
      if (parsed.version() != 4) {
        errors.add(new FieldError("event_id",
            "must be a valid UUID v4 format"));
      }
    } catch (IllegalArgumentException e) {
      errors.add(new FieldError("event_id",
          "must be a valid UUID v4 format"));
    }
  }

  private void validateEventType(String eventType, List<FieldError> errors) {
    if (eventType == null || eventType.isBlank()) {
      return; // handled by @NotBlank
    }
    if (!VALID_EVENT_TYPES.contains(eventType)) {
      errors.add(new FieldError("event_type",
          "must be one of: BET, DEPOSIT, SESSION, GAME_RESULT"));
    }
  }

  private void validateClientTimestamp(Long clientTimestamp, List<FieldError> errors) {
    if (clientTimestamp == null) {
      return; // handled by @NotNull
    }
    Instant eventTime = Instant.ofEpochMilli(clientTimestamp);
    Instant now = Instant.now();
    Duration age = Duration.between(eventTime, now).abs();
    if (age.compareTo(MAX_CLOCK_SKEW) > 0) {
      errors.add(new FieldError("client_timestamp",
          "must be within 48 hours of server time"));
    }
  }
}
