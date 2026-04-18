package com.entain.betsafe.gateway.archive;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Kafka consumer that archives events to MinIO as Parquet files.
 * Buffers events in memory — flushes on: flush-count events OR flush-interval seconds.
 * Consumer group: betsafe-archive-consumer.
 * Parquet compression: SNAPPY (AR-FR-003).
 */
@Component
public class ParquetEventArchiveConsumer {

  private static final Logger log =
      LoggerFactory.getLogger(ParquetEventArchiveConsumer.class);
  private static final DateTimeFormatter PATH_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

  /**
   * Avro schema mirroring PlayerEvent envelope — all fields present.
   */
  private static final String PARQUET_SCHEMA_JSON = """
      {
        "type": "record",
        "name": "PlayerEvent",
        "namespace": "com.entain.betsafe.common.event",
        "fields": [
          {"name": "event_id", "type": "string"},
          {"name": "event_type", "type": "string"},
          {"name": "player_id", "type": "string"},
          {"name": "tenant_id", "type": "string"},
          {"name": "brand_id", "type": "string"},
          {"name": "client_timestamp", "type": "long"},
          {"name": "server_ingest_timestamp", "type": "long"},
          {"name": "schema_version", "type": "int"},
          {"name": "payload", "type": "string"}
        ]
      }
      """;

  private static final Schema AVRO_SCHEMA =
      new Schema.Parser().parse(PARQUET_SCHEMA_JSON);

  private final S3AsyncClient s3Client;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;
  private final ArchiveHealthIndicator healthIndicator;
  private final String bucketName;
  private final int flushCount;
  private final ReentrantLock bufferLock = new ReentrantLock();
  private final List<Map<String, Object>> buffer = new ArrayList<>();
  private final List<Acknowledgment> pendingAcks = new ArrayList<>();
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor();

  public ParquetEventArchiveConsumer(
      S3AsyncClient s3Client,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry,
      ArchiveHealthIndicator healthIndicator,
      @Value("${betsafe.minio.bucket-name}") String bucketName,
      @Value("${betsafe.archive.flush-count}") int flushCount,
      @Value("${betsafe.archive.flush-interval-seconds}") int flushIntervalSec) {
    this.s3Client = s3Client;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
    this.healthIndicator = healthIndicator;
    this.bucketName = bucketName;
    this.flushCount = flushCount;

    scheduler.scheduleAtFixedRate(this::timedFlush,
        flushIntervalSec, flushIntervalSec, TimeUnit.SECONDS);
  }

  @KafkaListener(
      topicPattern = "betsafe\\.events\\..*",
      groupId = "betsafe-archive-consumer",
      containerFactory = "archiveKafkaListenerContainerFactory"
  )
  public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
    try {
      Map<String, Object> event = objectMapper.readValue(
          record.value(), new TypeReference<>() {
          });

      bufferLock.lock();
      try {
        buffer.add(event);
        pendingAcks.add(ack);
        healthIndicator.recordBuffered();

        if (buffer.size() >= flushCount) {
          flush();
        }
      } finally {
        bufferLock.unlock();
      }
    } catch (Exception e) {
      log.error("Archive consumer error: topic={}, partition={}, offset={}",
          record.topic(), record.partition(), record.offset(), e);
      ack.acknowledge();
    }
  }

  private void timedFlush() {
    bufferLock.lock();
    try {
      if (!buffer.isEmpty()) {
        flush();
      }
    } catch (Exception e) {
      log.error("Timed flush failed", e);
    } finally {
      bufferLock.unlock();
    }
  }

  private void flush() {
    if (buffer.isEmpty()) {
      return;
    }

    List<Map<String, Object>> batch = new ArrayList<>(buffer);
    List<Acknowledgment> acks = new ArrayList<>(pendingAcks);
    buffer.clear();
    pendingAcks.clear();

    String tenantId = (String) batch.get(0)
        .getOrDefault("tenant_id", "unknown");
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    String objectKey = buildObjectKey(tenantId, now);

    try {
      File parquetFile = writeParquetFile(batch);
      uploadWithRetry(parquetFile, acks, objectKey, tenantId, 0);
    } catch (IOException e) {
      log.error("Failed to write Parquet file for batch of {} events",
          batch.size(), e);
    }
  }

  /**
   * Writes events to a local temp Parquet file with SNAPPY compression.
   */
  private File writeParquetFile(List<Map<String, Object>> events)
      throws IOException {
    File tempFile = Files.createTempFile("betsafe-archive-", ".parquet").toFile();
    tempFile.deleteOnExit();

    // Delete the empty file so ParquetWriter can create it
    tempFile.delete();

    Configuration hadoopConf = new Configuration();
    org.apache.hadoop.fs.Path path =
        new org.apache.hadoop.fs.Path(tempFile.getAbsolutePath());

    try (ParquetWriter<GenericRecord> writer = AvroParquetWriter
        .<GenericRecord>builder(path)
        .withSchema(AVRO_SCHEMA)
        .withCompressionCodec(CompressionCodecName.SNAPPY)
        .withConf(hadoopConf)
        .build()) {

      for (Map<String, Object> event : events) {
        GenericRecord record = new GenericData.Record(AVRO_SCHEMA);
        record.put("event_id", str(event, "event_id"));
        record.put("event_type", str(event, "event_type"));
        record.put("player_id", str(event, "player_id"));
        record.put("tenant_id", str(event, "tenant_id"));
        record.put("brand_id", str(event, "brand_id"));
        record.put("client_timestamp",
            ((Number) event.getOrDefault("client_timestamp", 0L)).longValue());
        record.put("server_ingest_timestamp",
            ((Number) event.getOrDefault("server_ingest_timestamp", 0L))
                .longValue());
        record.put("schema_version",
            ((Number) event.getOrDefault("schema_version", 1)).intValue());
        record.put("payload",
            objectMapper.writeValueAsString(event.get("payload")));
        writer.write(record);
      }
    }
    return tempFile;
  }

  private void uploadWithRetry(File parquetFile, List<Acknowledgment> acks,
      String objectKey, String tenantId, int attempt) {
    try {
      byte[] data = Files.readAllBytes(parquetFile.toPath());

      PutObjectRequest putRequest = PutObjectRequest.builder()
          .bucket(bucketName)
          .key(objectKey)
          .contentType("application/octet-stream")
          .build();

      CompletableFuture<?> future = s3Client.putObject(putRequest,
          AsyncRequestBody.fromBytes(data));

      future.whenComplete((resp, ex) -> {
        if (ex != null) {
          if (attempt < 3) {
            long delay = (long) Math.pow(2, attempt) * 1000;
            log.warn("MinIO upload failed (attempt {}), retrying in {}ms: {}",
                attempt + 1, delay, objectKey);
            scheduler.schedule(
                () -> uploadWithRetry(parquetFile, acks, objectKey,
                    tenantId, attempt + 1),
                delay, TimeUnit.MILLISECONDS);
          } else {
            log.error("MinIO upload failed after 3 retries: {}",
                objectKey, ex);
            parquetFile.delete();
          }
        } else {
          acks.forEach(Acknowledgment::acknowledge);
          healthIndicator.recordFlush(objectKey, acks.size());

          Counter.builder("betsafe_archive_files_uploaded_total")
              .tag("tenant_id", tenantId)
              .register(meterRegistry)
              .increment();

          log.info("Archived {} events to MinIO: {}",
              acks.size(), objectKey);
          parquetFile.delete();
        }
      });
    } catch (IOException e) {
      log.error("Failed to read Parquet file for upload", e);
      parquetFile.delete();
    }
  }

  private String buildObjectKey(String tenantId, LocalDateTime now) {
    return String.format(
        "raw-events/tenant=%s/year=%d/month=%02d/day=%02d/%s_%s_%s.parquet",
        tenantId, now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
        tenantId, now.format(PATH_FORMAT),
        UUID.randomUUID().toString().substring(0, 8));
  }

  private String str(Map<String, Object> map, String key) {
    Object val = map.get(key);
    return val != null ? val.toString() : "";
  }

  @PreDestroy
  public void shutdown() {
    log.info("Shutting down archive consumer — flushing remaining buffer");
    bufferLock.lock();
    try {
      if (!buffer.isEmpty()) {
        flush();
      }
    } finally {
      bufferLock.unlock();
    }
    scheduler.shutdown();
  }
}
