package com.entain.betsafe.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Event simulator CLI — generates realistic player events at configurable EPS.
 * No Spring Boot web server — runs from CLI.
 */
public class SimulatorMain {

  private static final AtomicBoolean running = new AtomicBoolean(true);

  public static void main(String[] args) throws Exception {
    SimulatorConfig config = SimulatorConfig.fromArgs(args);
    ObjectMapper mapper = new ObjectMapper();
    SimulatorMetrics metrics = new SimulatorMetrics();
    EventGenerator generator = new EventGenerator(config);

    HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    // Initialize players with UUID-based IDs
    List<PlayerState> players = new ArrayList<>();
    for (int i = 0; i < config.playerCount(); i++) {
      players.add(new PlayerState("player-" + UUID.randomUUID()));
    }

    // Graceful shutdown on Ctrl+C
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      running.set(false);
      metrics.printFinalSummary();
    }));

    ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(4);

    // Metrics printer — every 10 seconds
    scheduler.scheduleAtFixedRate(() -> {
      int activeSessions = (int) players.stream()
          .filter(p -> p.getState() != PlayerState.State.IDLE
              && p.getState() != PlayerState.State.SESSION_ENDED)
          .count();
      int highRisk = (int) players.stream()
          .filter(PlayerState::isHighRisk)
          .count();
      metrics.printSummary(activeSessions, highRisk);
    }, 10, 10, TimeUnit.SECONDS);

    // Duration-based stop
    if (config.durationSeconds() > 0) {
      scheduler.schedule(() -> running.set(false),
          config.durationSeconds(), TimeUnit.SECONDS);
    }

    // Event generation at fixed rate
    long intervalMicros = 1_000_000L / config.eps();
    int playerIndex = 0;

    System.out.printf("Starting simulator: eps=%d, players=%d, tenants=%s, target=%s%n",
        config.eps(), config.playerCount(), config.tenantIds(), config.targetUrl());

    while (running.get()) {
      PlayerState player = players.get(playerIndex % players.size());
      String tenantId = config.tenantIds().get(
          playerIndex % config.tenantIds().size());

      Map<String, Object> event = generator.generateEvent(player, tenantId);
      String body = mapper.writeValueAsString(event);

      scheduler.submit(() -> sendEvent(httpClient, config.targetUrl(),
          body, metrics));

      playerIndex++;

      // Rate control — microsecond precision
      long sleepNanos = intervalMicros * 1000;
      long start = System.nanoTime();
      while (System.nanoTime() - start < sleepNanos && running.get()) {
        Thread.onSpinWait();
      }
    }

    scheduler.shutdown();
    scheduler.awaitTermination(10, TimeUnit.SECONDS);
    System.exit(0);
  }

  private static void sendEvent(HttpClient client, String targetUrl,
      String body, SimulatorMetrics metrics) {
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(targetUrl + "/api/v1/events"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .timeout(Duration.ofSeconds(5))
          .build();

      metrics.recordSent();

      HttpResponse<String> response = client.send(request,
          HttpResponse.BodyHandlers.ofString());

      switch (response.statusCode()) {
        case 202 -> metrics.recordAccepted();
        case 400 -> metrics.recordRejected();
        case 429 -> {
          metrics.recordRateLimited();
          Thread.sleep(1000); // Back off 1 second on rate limit
        }
        default -> metrics.recordError();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      metrics.recordError();
    }
  }
}
