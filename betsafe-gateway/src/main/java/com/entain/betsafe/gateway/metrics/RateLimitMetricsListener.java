package com.entain.betsafe.gateway.metrics;

import com.entain.betsafe.gateway.ratelimit.RateLimitExceededEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens for rate limit exceeded events and increments Prometheus counter.
 */
@Component
public class RateLimitMetricsListener {

  private final MeterRegistry meterRegistry;

  public RateLimitMetricsListener(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @EventListener
  public void onRateLimitExceeded(RateLimitExceededEvent event) {
    Counter.builder("betsafe_rate_limit_exceeded_total")
        .tag("tenant_id", event.getTenantId())
        .register(meterRegistry)
        .increment();
  }
}
