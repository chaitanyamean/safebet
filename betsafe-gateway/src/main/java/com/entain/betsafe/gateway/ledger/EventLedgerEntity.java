package com.entain.betsafe.gateway.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only audit ledger entity — no UPDATE or DELETE permitted.
 * player_id is NEVER stored — only player_id_hash (SHA-256).
 */
@Entity
@Table(name = "event_ledger")
public class EventLedgerEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_id", nullable = false, unique = true)
  private UUID eventId;

  @Column(name = "event_type", nullable = false, length = 50)
  private String eventType;

  @Column(name = "player_id_hash", nullable = false, length = 64)
  private String playerIdHash;

  @Column(name = "tenant_id", nullable = false, length = 100)
  private String tenantId;

  @Column(name = "brand_id", nullable = false, length = 100)
  private String brandId;

  @Column(name = "client_timestamp", nullable = false)
  private OffsetDateTime clientTimestamp;

  @Column(name = "server_ingest_timestamp", nullable = false)
  private OffsetDateTime serverIngestTimestamp;

  @Column(name = "kafka_topic", length = 200)
  private String kafkaTopic;

  @Column(name = "kafka_partition")
  private Integer kafkaPartition;

  @Column(name = "kafka_offset")
  private Long kafkaOffset;

  @Column(name = "schema_version", nullable = false)
  private Integer schemaVersion = 1;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private String payload;

  @Column(name = "row_hash", length = 64)
  private String rowHash;

  @Column(name = "created_at", nullable = false, updatable = false,
      insertable = false)
  private OffsetDateTime createdAt;

  public EventLedgerEntity() {
  }

  // Getters and setters

  public Long getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public void setEventId(UUID eventId) {
    this.eventId = eventId;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getPlayerIdHash() {
    return playerIdHash;
  }

  public void setPlayerIdHash(String playerIdHash) {
    this.playerIdHash = playerIdHash;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getBrandId() {
    return brandId;
  }

  public void setBrandId(String brandId) {
    this.brandId = brandId;
  }

  public OffsetDateTime getClientTimestamp() {
    return clientTimestamp;
  }

  public void setClientTimestamp(OffsetDateTime clientTimestamp) {
    this.clientTimestamp = clientTimestamp;
  }

  public OffsetDateTime getServerIngestTimestamp() {
    return serverIngestTimestamp;
  }

  public void setServerIngestTimestamp(OffsetDateTime serverIngestTimestamp) {
    this.serverIngestTimestamp = serverIngestTimestamp;
  }

  public String getKafkaTopic() {
    return kafkaTopic;
  }

  public void setKafkaTopic(String kafkaTopic) {
    this.kafkaTopic = kafkaTopic;
  }

  public Integer getKafkaPartition() {
    return kafkaPartition;
  }

  public void setKafkaPartition(Integer kafkaPartition) {
    this.kafkaPartition = kafkaPartition;
  }

  public Long getKafkaOffset() {
    return kafkaOffset;
  }

  public void setKafkaOffset(Long kafkaOffset) {
    this.kafkaOffset = kafkaOffset;
  }

  public Integer getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(Integer schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public String getRowHash() {
    return rowHash;
  }

  public void setRowHash(String rowHash) {
    this.rowHash = rowHash;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
