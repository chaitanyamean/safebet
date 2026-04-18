package com.entain.betsafe.gateway.ledger;

import com.entain.betsafe.common.util.HashUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer that writes events to the PostgreSQL audit ledger.
 * Consumer group: betsafe-ledger-consumer.
 * Manual offset commit — offset committed only after successful DB write.
 */
@Component
public class LedgerConsumer {

  private static final Logger log = LoggerFactory.getLogger(LedgerConsumer.class);

  private final EventLedgerRepository repository;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  public LedgerConsumer(EventLedgerRepository repository,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
  }

  @KafkaListener(
      topicPattern = "betsafe\\.events\\..*",
      groupId = "betsafe-ledger-consumer",
      containerFactory = "kafkaListenerContainerFactory"
  )
  public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      Map<String, Object> event = objectMapper.readValue(
          record.value(), new TypeReference<>() {
          });

      String eventIdStr = (String) event.get("event_id");
      UUID eventId = UUID.fromString(eventIdStr);

      // Duplicate detection — skip if already exists
      if (repository.existsByEventId(eventId)) {
        log.warn("Duplicate event_id detected, skipping: {}", eventIdStr);
        ack.acknowledge();
        return;
      }

      String eventType = (String) event.get("event_type");
      String playerId = (String) event.get("player_id");
      String tenantId = (String) event.get("tenant_id");
      String brandId = (String) event.get("brand_id");
      long clientTs = ((Number) event.get("client_timestamp")).longValue();
      long serverTs = ((Number) event.get("server_ingest_timestamp")).longValue();
      int schemaVersion = event.containsKey("schema_version")
          ? ((Number) event.get("schema_version")).intValue() : 1;

      String playerIdHash = HashUtil.sha256(playerId);
      String payloadJson = objectMapper.writeValueAsString(event.get("payload"));
      String rowHash = HashUtil.computeRowHash(eventIdStr, eventType,
          playerIdHash, payloadJson);

      EventLedgerEntity entity = new EventLedgerEntity();
      entity.setEventId(eventId);
      entity.setEventType(eventType);
      entity.setPlayerIdHash(playerIdHash);
      entity.setTenantId(tenantId);
      entity.setBrandId(brandId);
      entity.setClientTimestamp(
          OffsetDateTime.ofInstant(Instant.ofEpochMilli(clientTs), ZoneOffset.UTC));
      entity.setServerIngestTimestamp(
          OffsetDateTime.ofInstant(Instant.ofEpochMilli(serverTs), ZoneOffset.UTC));
      entity.setKafkaTopic(record.topic());
      entity.setKafkaPartition(record.partition());
      entity.setKafkaOffset(record.offset());
      entity.setSchemaVersion(schemaVersion);
      entity.setPayload(payloadJson);
      entity.setRowHash(rowHash);

      repository.save(entity);

      // Commit offset only after successful write
      ack.acknowledge();

      Counter.builder("betsafe_ledger_writes_total")
          .tag("tenant_id", tenantId)
          .register(meterRegistry)
          .increment();

      sample.stop(Timer.builder("betsafe_ledger_write_latency")
          .tag("tenant_id", tenantId)
          .register(meterRegistry));

      log.info("Ledger write: event_id={}, tenant_id={}, player_id_hash={}",
          eventIdStr, tenantId, playerIdHash);

    } catch (JsonProcessingException e) {
      log.error("Failed to deserialize Kafka message: topic={}, partition={}, offset={}",
          record.topic(), record.partition(), record.offset(), e);
      ack.acknowledge(); // Skip malformed messages
    } catch (Exception e) {
      log.error("Ledger write failed: topic={}, partition={}, offset={}",
          record.topic(), record.partition(), record.offset(), e);
      // Do NOT acknowledge — message will be redelivered
    }
  }
}
