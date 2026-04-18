package com.entain.betsafe.gateway.kafka;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Creates Kafka topics programmatically on gateway startup.
 * Idempotent — existing topics are not recreated.
 */
@Component
public class KafkaTopicInitializer {

  private static final Logger log = LoggerFactory.getLogger(KafkaTopicInitializer.class);

  private final KafkaAdmin kafkaAdmin;
  private final List<String> tenants;
  private final String topicPrefix;
  private final String dlqTopic;
  private final int partitions;
  private final int dlqPartitions;

  public KafkaTopicInitializer(
      KafkaAdmin kafkaAdmin,
      @Value("${betsafe.kafka.topic-prefix}") String topicPrefix,
      @Value("${betsafe.kafka.dlq-topic}") String dlqTopic,
      @Value("${betsafe.kafka.partitions}") int partitions,
      @Value("${betsafe.kafka.dlq-partitions}") int dlqPartitions,
      @Value("#{'${betsafe.tenants}'.split(',')}") List<String> tenants) {
    this.kafkaAdmin = kafkaAdmin;
    this.topicPrefix = topicPrefix;
    this.dlqTopic = dlqTopic;
    this.partitions = partitions;
    this.dlqPartitions = dlqPartitions;
    this.tenants = tenants;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void createTopics() {
    try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
      Set<String> existingTopics = admin.listTopics().names().get(30, TimeUnit.SECONDS);

      List<NewTopic> topicsToCreate = new ArrayList<>();

      for (String tenant : tenants) {
        String topicName = topicPrefix + "." + tenant.trim();
        if (!existingTopics.contains(topicName)) {
          topicsToCreate.add(new NewTopic(topicName, partitions, (short) 1));
          log.info("Scheduling topic creation: {}", topicName);
        }
      }

      if (!existingTopics.contains(dlqTopic)) {
        topicsToCreate.add(new NewTopic(dlqTopic, dlqPartitions, (short) 1));
        log.info("Scheduling DLQ topic creation: {}", dlqTopic);
      }

      if (!topicsToCreate.isEmpty()) {
        admin.createTopics(topicsToCreate).all().get(30, TimeUnit.SECONDS);
        log.info("Created {} Kafka topics", topicsToCreate.size());
      } else {
        log.info("All Kafka topics already exist");
      }
    } catch (Exception e) {
      log.error("Failed to create Kafka topics", e);
      throw new RuntimeException("Kafka topic initialization failed", e);
    }
  }
}
