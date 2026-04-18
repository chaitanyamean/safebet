package com.entain.betsafe.simulator;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe metrics for the simulator — printed every 10 seconds.
 */
public class SimulatorMetrics {

  private final AtomicLong eventsSent = new AtomicLong();
  private final AtomicLong accepted = new AtomicLong();
  private final AtomicLong rejected = new AtomicLong();
  private final AtomicLong rateLimited = new AtomicLong();
  private final AtomicLong errors = new AtomicLong();
  private final long startTime = System.currentTimeMillis();

  public void recordSent() {
    eventsSent.incrementAndGet();
  }

  public void recordAccepted() {
    accepted.incrementAndGet();
  }

  public void recordRejected() {
    rejected.incrementAndGet();
  }

  public void recordRateLimited() {
    rateLimited.incrementAndGet();
  }

  public void recordError() {
    errors.incrementAndGet();
  }

  public void printSummary(int activeSessions, int highRiskPlayers) {
    long elapsed = (System.currentTimeMillis() - startTime) / 1000;
    double currentEps = elapsed > 0 ? (double) eventsSent.get() / elapsed : 0;

    System.out.printf(
        "[%ds] sent=%d accepted=%d rejected=%d rate_limited=%d "
            + "errors=%d eps=%.1f active_sessions=%d high_risk=%d%n",
        elapsed, eventsSent.get(), accepted.get(), rejected.get(),
        rateLimited.get(), errors.get(), currentEps,
        activeSessions, highRiskPlayers);
  }

  public void printFinalSummary() {
    long elapsed = (System.currentTimeMillis() - startTime) / 1000;
    System.out.println("\n=== FINAL SUMMARY ===");
    System.out.printf("Duration: %ds%n", elapsed);
    System.out.printf("Total sent: %d%n", eventsSent.get());
    System.out.printf("Accepted: %d%n", accepted.get());
    System.out.printf("Rejected: %d%n", rejected.get());
    System.out.printf("Rate limited: %d%n", rateLimited.get());
    System.out.printf("Errors: %d%n", errors.get());
    System.out.printf("Avg EPS: %.1f%n",
        elapsed > 0 ? (double) eventsSent.get() / elapsed : 0);
  }
}
