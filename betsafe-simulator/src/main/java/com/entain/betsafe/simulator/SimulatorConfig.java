package com.entain.betsafe.simulator;

import java.util.List;

/**
 * CLI configuration for the event simulator.
 */
public record SimulatorConfig(
    int eps,
    int playerCount,
    List<String> tenantIds,
    int sessionDurationMinutes,
    double depositProbability,
    double betProbability,
    double winProbability,
    String targetUrl,
    int durationSeconds
) {

  public static SimulatorConfig fromArgs(String[] args) {
    int eps = 50;
    int playerCount = 100;
    List<String> tenantIds = List.of("ladbrokes");
    int sessionDurationMinutes = 30;
    double depositProbability = 0.05;
    double betProbability = 0.70;
    double winProbability = 0.45;
    String targetUrl = "http://localhost:8090";
    int durationSeconds = 0;

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--eps" -> eps = Integer.parseInt(args[++i]);
        case "--player-count" -> playerCount = Integer.parseInt(args[++i]);
        case "--tenant-ids" -> tenantIds = List.of(args[++i].split(","));
        case "--session-duration-minutes" ->
            sessionDurationMinutes = Integer.parseInt(args[++i]);
        case "--deposit-probability" ->
            depositProbability = Double.parseDouble(args[++i]);
        case "--bet-probability" -> betProbability = Double.parseDouble(args[++i]);
        case "--win-probability" -> winProbability = Double.parseDouble(args[++i]);
        case "--target-url" -> targetUrl = args[++i];
        case "--duration-seconds" -> durationSeconds = Integer.parseInt(args[++i]);
        default -> { /* ignore unknown args */ }
      }
    }

    return new SimulatorConfig(eps, playerCount, tenantIds, sessionDurationMinutes,
        depositProbability, betProbability, winProbability, targetUrl, durationSeconds);
  }
}
