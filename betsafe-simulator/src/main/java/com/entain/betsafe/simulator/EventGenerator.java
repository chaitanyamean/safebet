package com.entain.betsafe.simulator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Generates realistic player events based on player state and configuration.
 */
public class EventGenerator {

  private final SimulatorConfig config;
  private final Random random = new Random();

  private static final String[] CURRENCIES = {"GBP", "EUR", "USD"};
  private static final String[] PAYMENT_METHODS =
      {"CARD", "BANK_TRANSFER", "E_WALLET"};
  private static final String[] GAME_TYPES =
      {"SPORTS", "CASINO", "POKER", "VIRTUAL"};
  private static final String[] DEVICE_TYPES =
      {"DESKTOP", "MOBILE", "TABLET", "APP"};

  public EventGenerator(SimulatorConfig config) {
    this.config = config;
  }

  public Map<String, Object> generateEvent(PlayerState player, String tenantId) {
    Map<String, Object> event = new HashMap<>();
    event.put("event_id", UUID.randomUUID().toString());
    event.put("player_id", player.getPlayerId());
    event.put("tenant_id", tenantId);
    event.put("brand_id", tenantId + "-brand");
    event.put("client_timestamp", System.currentTimeMillis());

    switch (player.getState()) {
      case IDLE -> {
        player.startSession();
        event.put("event_type", "SESSION");
        event.put("payload", buildSessionPayload(player, "START"));
      }
      case SESSION_STARTED -> {
        player.startBetting();
        event.put("event_type", "SESSION");
        event.put("payload", buildSessionPayload(player, "HEARTBEAT"));
      }
      case BETTING -> {
        double roll = random.nextDouble();
        double depositProb = player.isHighRisk() ? 0.40 : config.depositProbability();
        player.decrementHighRiskCounter();

        if (roll < depositProb) {
          event.put("event_type", "DEPOSIT");
          event.put("payload", buildDepositPayload());
        } else if (roll < depositProb + config.betProbability()) {
          event.put("event_type", "BET");
          event.put("payload", buildBetPayload());
        } else {
          event.put("event_type", "GAME_RESULT");
          event.put("payload", buildGameResultPayload(player));
        }

        // Check if session should end
        long sessionDuration = System.currentTimeMillis() - player.getSessionStartTime();
        if (sessionDuration > config.sessionDurationMinutes() * 60_000L) {
          player.endSession();
        }
      }
      case SESSION_ENDED -> {
        event.put("event_type", "SESSION");
        event.put("payload", buildSessionPayload(player, "END"));
        player.resetToIdle();
      }
    }

    return event;
  }

  private Map<String, Object> buildSessionPayload(PlayerState player, String subtype) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("session_id", player.getSessionId());
    payload.put("event_subtype", subtype);
    payload.put("device_type", DEVICE_TYPES[random.nextInt(DEVICE_TYPES.length)]);
    payload.put("ip_address_hash", sha256("192.168.1." + random.nextInt(255)));
    return payload;
  }

  private Map<String, Object> buildBetPayload() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("bet_id", UUID.randomUUID().toString());
    payload.put("market_id", "market-" + random.nextInt(10000));
    payload.put("stake", generateStake().toPlainString());
    payload.put("currency", CURRENCIES[random.nextInt(CURRENCIES.length)]);
    payload.put("game_type", GAME_TYPES[random.nextInt(GAME_TYPES.length)]);
    payload.put("odds", BigDecimal.valueOf(1.1 + random.nextDouble() * 20)
        .setScale(2, RoundingMode.HALF_UP).toPlainString());
    payload.put("is_in_play", random.nextBoolean());
    return payload;
  }

  private Map<String, Object> buildDepositPayload() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("transaction_id", UUID.randomUUID().toString());
    payload.put("amount", generateStake().multiply(BigDecimal.TEN)
        .setScale(2, RoundingMode.HALF_UP).toPlainString());
    payload.put("currency", CURRENCIES[random.nextInt(CURRENCIES.length)]);
    payload.put("payment_method",
        PAYMENT_METHODS[random.nextInt(PAYMENT_METHODS.length)]);
    payload.put("is_first_deposit", random.nextDouble() < 0.05);
    return payload;
  }

  private Map<String, Object> buildGameResultPayload(PlayerState player) {
    Map<String, Object> payload = new HashMap<>();
    BigDecimal stake = generateStake();
    boolean isWin = random.nextDouble() < config.winProbability();

    if (isWin) {
      player.recordWin();
    } else {
      player.recordLoss();
    }

    payload.put("game_id", UUID.randomUUID().toString());
    payload.put("game_type", GAME_TYPES[random.nextInt(GAME_TYPES.length)]);
    payload.put("result", isWin ? "WIN" : "LOSS");
    payload.put("stake", stake.toPlainString());
    payload.put("return_amount", isWin
        ? stake.multiply(BigDecimal.valueOf(1 + random.nextDouble() * 5))
            .setScale(2, RoundingMode.HALF_UP).toPlainString()
        : "0.00");
    payload.put("session_id", player.getSessionId());
    return payload;
  }

  /**
   * Stake distribution: £1–£10 (60%), £11–£100 (30%), £101–£500 (10%).
   */
  private BigDecimal generateStake() {
    double roll = random.nextDouble();
    double amount;
    if (roll < 0.60) {
      amount = 1 + random.nextDouble() * 9;
    } else if (roll < 0.90) {
      amount = 11 + random.nextDouble() * 89;
    } else {
      amount = 101 + random.nextDouble() * 399;
    }
    return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
  }

  private String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
