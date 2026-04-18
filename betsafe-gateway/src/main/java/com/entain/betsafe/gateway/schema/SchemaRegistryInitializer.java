package com.entain.betsafe.gateway.schema;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Registers all Avro schemas in Schema Registry on gateway startup.
 * Idempotent — re-running with existing schemas does not throw errors.
 */
@Component
public class SchemaRegistryInitializer {

  private static final Logger log = LoggerFactory.getLogger(SchemaRegistryInitializer.class);

  private final String schemaRegistryUrl;

  public SchemaRegistryInitializer(
      @Value("${betsafe.schema-registry.url}") String schemaRegistryUrl) {
    this.schemaRegistryUrl = schemaRegistryUrl;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void registerSchemas() {
    SchemaRegistryClient client = new CachedSchemaRegistryClient(schemaRegistryUrl, 20);
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    try {
      Resource[] resources = resolver.getResources("classpath*:**/avro/*.avsc");
      for (Resource resource : resources) {
        registerSchema(client, resource);
      }
    } catch (IOException e) {
      log.error("Failed to load Avro schema files", e);
    }
  }

  private void registerSchema(SchemaRegistryClient client, Resource resource) {
    try (InputStream is = resource.getInputStream()) {
      Schema schema = new Schema.Parser().parse(is);
      String subject = schema.getFullName();
      AvroSchema avroSchema = new AvroSchema(schema);

      // Set BACKWARD_TRANSITIVE compatibility mode (SC-003)
      try {
        client.updateCompatibility(subject, "BACKWARD_TRANSITIVE");
      } catch (RestClientException e) {
        log.debug("Compatibility mode already set for {}: {}",
            subject, e.getMessage());
      }

      client.register(subject, avroSchema);
      log.info("Registered schema: {} (subject={}, compatibility=BACKWARD_TRANSITIVE)",
          schema.getName(), subject);
    } catch (RestClientException e) {
      log.warn("Schema registration warning for {}: {}",
          resource.getFilename(), e.getMessage());
    } catch (IOException e) {
      log.error("Failed to register schema: {}", resource.getFilename(), e);
    }
  }
}
