package com.entain.betsafe.gateway.controller;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Kafka connectivity status endpoint — used by operations team.
 */
@RestController
@RequestMapping("/api/v1/health")
public class KafkaHealthController {

  private static final Logger log = LoggerFactory.getLogger(KafkaHealthController.class);

  private final KafkaAdmin kafkaAdmin;

  public KafkaHealthController(KafkaAdmin kafkaAdmin) {
    this.kafkaAdmin = kafkaAdmin;
  }

  @GetMapping("/kafka")
  public ResponseEntity<Map<String, Object>> kafkaHealth() {
    Map<String, Object> health = new HashMap<>();
    try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
      DescribeClusterResult cluster = admin.describeCluster();
      health.put("status", "UP");
      health.put("clusterId", cluster.clusterId().get(5, TimeUnit.SECONDS));
      health.put("nodeCount", cluster.nodes().get(5, TimeUnit.SECONDS).size());
      health.put("topics", admin.listTopics().names().get(5, TimeUnit.SECONDS));
      return ResponseEntity.ok(health);
    } catch (Exception e) {
      log.error("Kafka health check failed", e);
      health.put("status", "DOWN");
      health.put("error", e.getMessage());
      return ResponseEntity.status(503).body(health);
    }
  }
}
