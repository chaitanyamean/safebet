package com.entain.betsafe.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.net.URI;

/**
 * MinIO S3 client configuration — creates bucket on startup if not exists.
 */
@Configuration
public class MinioConfig {

  private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

  @Value("${betsafe.minio.endpoint}")
  private String endpoint;

  @Value("${betsafe.minio.access-key}")
  private String accessKey;

  @Value("${betsafe.minio.secret-key}")
  private String secretKey;

  @Value("${betsafe.minio.bucket-name}")
  private String bucketName;

  @Bean
  public S3AsyncClient s3AsyncClient() {
    return S3AsyncClient.builder()
        .endpointOverride(URI.create(endpoint))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey)))
        .region(Region.US_EAST_1)
        .forcePathStyle(true)
        .build();
  }

  @Bean
  public S3Client s3Client() {
    return S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey)))
        .region(Region.US_EAST_1)
        .forcePathStyle(true)
        .build();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void createBucket() {
    try (S3Client client = s3Client()) {
      try {
        client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        log.info("MinIO bucket already exists: {}", bucketName);
      } catch (NoSuchBucketException e) {
        client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        log.info("Created MinIO bucket: {}", bucketName);
      }
    } catch (Exception e) {
      log.error("Failed to initialize MinIO bucket: {}", bucketName, e);
    }
  }
}
