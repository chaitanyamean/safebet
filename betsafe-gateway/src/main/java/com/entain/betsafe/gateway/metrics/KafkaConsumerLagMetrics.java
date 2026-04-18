package com.entain.betsafe.gateway.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Exposes Kafka consumer lag as Prometheus gauge per consumer group, topic, partition.
 * This is the primary pipeline health signal (OB-FR-006).
 */
@Component
public class KafkaConsumerLagMetrics {

  private static final Logger log =
      LoggerFactory.getLogger(KafkaConsumerLagMetrics.class);

  private static final List<String> CONSUMER_GROUPS = List.of(
      "betsafe-ledger-consumer",
      "betsafe-archive-consumer"
  );

  private final KafkaAdmin kafkaAdmin;
  private final MeterRegistry meterRegistry;
  private final ConcurrentHashMap<String, Long> lagValues = new ConcurrentHashMap<>();

  public KafkaConsumerLagMetrics(KafkaAdmin kafkaAdmin,
      MeterRegistry meterRegistry) {
    this.kafkaAdmin = kafkaAdmin;
    this.meterRegistry = meterRegistry;
  }

  @Scheduled(fixedRate = 15000)
  public void collectLag() {
    try (AdminClient admin =
        AdminClient.create(kafkaAdmin.getConfigurationProperties())) {

      for (String groupId : CONSUMER_GROUPS) {
        collectGroupLag(admin, groupId);
      }
    } catch (Exception e) {
      log.debug("Failed to collect consumer lag metrics", e);
    }
  }

  private void collectGroupLag(AdminClient admin, String groupId) {
    try {
      ListConsumerGroupOffsetsResult offsetsResult =
          admin.listConsumerGroupOffsets(groupId);
      Map<TopicPartition, OffsetAndMetadata> offsets =
          offsetsResult.partitionsToOffsetAndMetadata()
              .get(10, TimeUnit.SECONDS);

      if (offsets == null || offsets.isEmpty()) {
        return;
      }

      Map<TopicPartition, Long> endOffsets =
          admin.listOffsets(
              offsets.keySet().stream().collect(
                  java.util.stream.Collectors.toMap(
                      tp -> tp,
                      tp -> org.apache.kafka.clients.admin.OffsetSpec.latest()
                  )
              )
          ).all().get(10, TimeUnit.SECONDS)
              .entrySet().stream()
              .collect(java.util.stream.Collectors.toMap(
                  Map.Entry::getKey,
                  e -> e.getValue().offset()
              ));

      for (Map.Entry<TopicPartition, OffsetAndMetadata> entry
          : offsets.entrySet()) {
        TopicPartition tp = entry.getKey();
        long committed = entry.getValue().offset();
        long end = endOffsets.getOrDefault(tp, committed);
        long lag = Math.max(0, end - committed);

        String key = groupId + ":" + tp.topic() + ":" + tp.partition();
        lagValues.put(key, lag);

        // Register gauge if not already registered
        String gaugeKey = "lag_" + key;
        Gauge.builder("betsafe_kafka_consumer_lag", lagValues,
                map -> map.getOrDefault(key, 0L).doubleValue())
            .tag("consumer_group", groupId)
            .tag("topic", tp.topic())
            .tag("partition", String.valueOf(tp.partition()))
            .description("Kafka consumer lag per partition")
            .register(meterRegistry);
      }
    } catch (Exception e) {
      log.debug("Failed to collect lag for group {}", groupId, e);
    }
  }
}
