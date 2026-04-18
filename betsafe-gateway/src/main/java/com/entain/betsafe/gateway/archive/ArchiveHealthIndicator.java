package com.entain.betsafe.gateway.archive;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Health indicator for the Parquet archive — shows last flush time and buffer state.
 */
@Component
public class ArchiveHealthIndicator implements HealthIndicator {

  private final AtomicReference<Instant> lastFlushTime = new AtomicReference<>();
  private final AtomicLong eventsBuffered = new AtomicLong();
  private final AtomicReference<String> lastFileUploaded = new AtomicReference<>("none");

  @Override
  public Health health() {
    return Health.up()
        .withDetail("last_flush_time",
            lastFlushTime.get() != null ? lastFlushTime.get().toString() : "never")
        .withDetail("events_buffered", eventsBuffered.get())
        .withDetail("last_file_uploaded", lastFileUploaded.get())
        .build();
  }

  public void recordFlush(String fileName, long eventCount) {
    lastFlushTime.set(Instant.now());
    lastFileUploaded.set(fileName);
    eventsBuffered.set(0);
  }

  public void recordBuffered() {
    eventsBuffered.incrementAndGet();
  }
}
