package com.entain.betsafe.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Rate limit configuration — per-tenant token bucket settings.
 */
@Configuration
@ConfigurationProperties(prefix = "betsafe.rate-limits")
public class RateLimitConfig {

  private int defaultEps = 100;
  private long retryAfterMs = 1000;
  private Map<String, Integer> tenants = new HashMap<>();

  public int getDefaultEps() {
    return defaultEps;
  }

  public void setDefaultEps(int defaultEps) {
    this.defaultEps = defaultEps;
  }

  public long getRetryAfterMs() {
    return retryAfterMs;
  }

  public void setRetryAfterMs(long retryAfterMs) {
    this.retryAfterMs = retryAfterMs;
  }

  public Map<String, Integer> getTenants() {
    return tenants;
  }

  public void setTenants(Map<String, Integer> tenants) {
    this.tenants = tenants;
  }

  public int getEpsForTenant(String tenantId) {
    return tenants.getOrDefault(tenantId, defaultEps);
  }
}
