package com.entain.betsafe.gateway.kafka;

import com.entain.betsafe.gateway.dto.EventRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Async Kafka publisher — uses player_id as partition key.
 * sendAsync with CompletableFuture — request thread never blocks on Kafka broker.
 */
@Service
public class KafkaEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;
  private final String topicPrefix;

  public KafkaEventPublisher(
      KafkaTemplate<String, String> kafkaTemplate,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry,
      @Value("${betsafe.kafka.topic-prefix}") String topicPrefix) {
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
    this.topicPrefix = topicPrefix;
  }

  /**
   * Publishes event to tenant-specific Kafka topic asynchronously.
   * Partition key: player_id (raw string) — guarantees player event ordering.
   */
  public CompletableFuture<SendResult<String, String>> publishAsync(
      EventRequest request, long serverIngestTimestamp) {

    String topic = topicPrefix + "." + request.tenantId();
    String key = request.playerId();

    Timer.Sample sample = Timer.start(meterRegistry);

    try {
      Map<String, Object> kafkaPayload = buildKafkaPayload(request, serverIngestTimestamp);
      String value = objectMapper.writeValueAsString(kafkaPayload);

      return kafkaTemplate.send(topic, key, value)
          .whenComplete((result, ex) -> {
            sample.stop(Timer.builder("betsafe_event_ingestion_latency")
                .tag("tenant_id", request.tenantId())
                .register(meterRegistry));

            if (ex != null) {
              Counter.builder("betsafe_kafka_publish_failure_total")
                  .tag("tenant_id", request.tenantId())
                  .tag("topic", topic)
                  .register(meterRegistry)
                  .increment();
              log.error("Kafka publish failed for event_id={}, tenant_id={}",
                  request.eventId(), request.tenantId(), ex);
            } else {
              Counter.builder("betsafe_kafka_publish_success_total")
                  .tag("tenant_id", request.tenantId())
                  .tag("topic", topic)
                  .register(meterRegistry)
                  .increment();
            }
          });
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize event for Kafka: event_id={}", request.eventId(), e);
      return CompletableFuture.failedFuture(e);
    }
  }

  private Map<String, Object> buildKafkaPayload(EventRequest request,
      long serverIngestTimestamp) {
    Map<String, Object> envelope = new HashMap<>();
    envelope.put("event_id", request.eventId());
    envelope.put("event_type", request.eventType());
    envelope.put("player_id", request.playerId());
    envelope.put("tenant_id", request.tenantId());
    envelope.put("brand_id", request.brandId());
    envelope.put("client_timestamp", request.clientTimestamp());
    envelope.put("server_ingest_timestamp", serverIngestTimestamp);
    envelope.put("schema_version", 1);
    envelope.put("payload", request.payload());
    return envelope;
  }
}
