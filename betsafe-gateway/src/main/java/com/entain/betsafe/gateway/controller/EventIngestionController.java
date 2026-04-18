package com.entain.betsafe.gateway.controller;

import com.entain.betsafe.common.util.HashUtil;
import com.entain.betsafe.gateway.config.RateLimitConfig;
import com.entain.betsafe.gateway.dto.ErrorResponse;
import com.entain.betsafe.gateway.dto.ErrorResponse.FieldError;
import com.entain.betsafe.gateway.dto.EventRequest;
import com.entain.betsafe.gateway.dto.EventResponse;
import com.entain.betsafe.gateway.kafka.KafkaEventPublisher;
import com.entain.betsafe.gateway.ratelimit.TenantRateLimiter;
import com.entain.betsafe.gateway.service.EventValidationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST endpoint for event ingestion — POST /api/v1/events.
 */
@RestController
@RequestMapping("/api/v1")
public class EventIngestionController {

  private static final Logger log = LoggerFactory.getLogger(EventIngestionController.class);

  private final EventValidationService validationService;
  private final TenantRateLimiter rateLimiter;
  private final KafkaEventPublisher kafkaPublisher;
  private final RateLimitConfig rateLimitConfig;
  private final MeterRegistry meterRegistry;

  public EventIngestionController(
      EventValidationService validationService,
      TenantRateLimiter rateLimiter,
      KafkaEventPublisher kafkaPublisher,
      RateLimitConfig rateLimitConfig,
      MeterRegistry meterRegistry) {
    this.validationService = validationService;
    this.rateLimiter = rateLimiter;
    this.kafkaPublisher = kafkaPublisher;
    this.rateLimitConfig = rateLimitConfig;
    this.meterRegistry = meterRegistry;
  }

  @PostMapping("/events")
  public CompletableFuture<ResponseEntity<?>> ingestEvent(
      @Valid @RequestBody EventRequest request) {

    String requestId = UUID.randomUUID().toString();
    long serverIngestTimestamp = Instant.now().toEpochMilli();
    String playerIdHash = HashUtil.sha256(request.playerId());

    MDC.put("request_id", requestId);
    MDC.put("tenant_id", request.tenantId());
    MDC.put("event_type", request.eventType());
    MDC.put("player_id_hash", playerIdHash);

    try {
      // Metrics: events received
      Counter.builder("betsafe_events_received_total")
          .tag("tenant_id", request.tenantId())
          .tag("event_type", request.eventType())
          .register(meterRegistry)
          .increment();

      // Validate event
      List<FieldError> errors = validationService.validate(request);
      if (!errors.isEmpty()) {
        Counter.builder("betsafe_events_rejected_total")
            .tag("tenant_id", request.tenantId())
            .tag("reason", "VALIDATION")
            .register(meterRegistry)
            .increment();
        log.warn("Event validation failed: event_id={}, errors={}",
            request.eventId(), errors.size());
        return CompletableFuture.completedFuture(
            ResponseEntity.badRequest()
                .header("X-Request-Id", requestId)
                .body(ErrorResponse.validation(requestId, errors)));
      }

      // Rate limit check
      if (!rateLimiter.tryAcquire(request.tenantId())) {
        Counter.builder("betsafe_events_rejected_total")
            .tag("tenant_id", request.tenantId())
            .tag("reason", "RATE_LIMIT")
            .register(meterRegistry)
            .increment();
        Counter.builder("betsafe_rate_limit_exceeded_total")
            .tag("tenant_id", request.tenantId())
            .register(meterRegistry)
            .increment();
        log.warn("Rate limit exceeded: tenant_id={}", request.tenantId());
        return CompletableFuture.completedFuture(
            ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("X-Request-Id", requestId)
                .header("X-RateLimit-Remaining", "0")
                .body(ErrorResponse.rateLimited(
                    requestId, request.tenantId(),
                    rateLimitConfig.getRetryAfterMs())));
      }

      // Publish to Kafka async
      return kafkaPublisher.publishAsync(request, serverIngestTimestamp)
          .thenApply(result -> {
            int partition = result.getRecordMetadata().partition();
            long offset = result.getRecordMetadata().offset();

            Counter.builder("betsafe_events_accepted_total")
                .tag("tenant_id", request.tenantId())
                .tag("event_type", request.eventType())
                .register(meterRegistry)
                .increment();

            log.info("Event accepted: event_id={}, partition={}, offset={}, latency_ms={}",
                request.eventId(), partition, offset,
                Instant.now().toEpochMilli() - serverIngestTimestamp);

            double remaining = rateLimiter.getAvailablePermits(request.tenantId());
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("X-Request-Id", requestId)
                .header("X-RateLimit-Remaining", String.valueOf((int) remaining))
                .body((Object) EventResponse.accepted(
                    request.eventId(), partition, offset));
          })
          .exceptionally(ex -> {
            log.error("Kafka publish failed: event_id={}", request.eventId(), ex);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("X-Request-Id", requestId)
                .body(ErrorResponse.unavailable(requestId));
          });
    } finally {
      MDC.clear();
    }
  }
}
