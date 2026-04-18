package com.entain.betsafe.gateway.ratelimit;

import org.springframework.context.ApplicationEvent;

/**
 * Spring ApplicationEvent emitted when a tenant's rate limit is exceeded.
 */
public class RateLimitExceededEvent extends ApplicationEvent {

  private final String tenantId;

  public RateLimitExceededEvent(Object source, String tenantId) {
    super(source);
    this.tenantId = tenantId;
  }

  public String getTenantId() {
    return tenantId;
  }
}
