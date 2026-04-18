package com.entain.betsafe.gateway.ratelimit;

import com.entain.betsafe.gateway.config.RateLimitConfig;
import com.google.common.util.concurrent.RateLimiter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-tenant token bucket rate limiter — Bulkhead Pattern.
 * Buckets created lazily on first request. tryAcquire with zero timeout — never blocks.
 */
@Component
public class TenantRateLimiter {

  private final RateLimitConfig config;
  private final ApplicationEventPublisher eventPublisher;
  private final ConcurrentMap<String, RateLimiter> buckets = new ConcurrentHashMap<>();

  public TenantRateLimiter(RateLimitConfig config,
      ApplicationEventPublisher eventPublisher) {
    this.config = config;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Attempts to acquire a permit for the given tenant.
   *
   * @return true if permit acquired, false if rate limit exceeded
   */
  public boolean tryAcquire(String tenantId) {
    RateLimiter limiter = buckets.computeIfAbsent(tenantId, this::createLimiter);
    boolean acquired = limiter.tryAcquire();
    if (!acquired) {
      eventPublisher.publishEvent(new RateLimitExceededEvent(this, tenantId));
    }
    return acquired;
  }

  /**
   * Returns approximate number of permits available for the tenant.
   */
  public double getAvailablePermits(String tenantId) {
    RateLimiter limiter = buckets.get(tenantId);
    if (limiter == null) {
      return config.getEpsForTenant(tenantId);
    }
    return limiter.getRate();
  }

  private RateLimiter createLimiter(String tenantId) {
    int eps = config.getEpsForTenant(tenantId);
    return RateLimiter.create(eps);
  }
}
