package com.entain.betsafe.simulator;

import java.util.UUID;

/**
 * Maintains realistic state per player: IDLE → SESSION_STARTED → BETTING → SESSION_ENDED.
 */
public class PlayerState {

  public enum State {
    IDLE, SESSION_STARTED, BETTING, SESSION_ENDED
  }

  private final String playerId;
  private State state;
  private String sessionId;
  private int consecutiveLosses;
  private int highRiskEventsRemaining;
  private long sessionStartTime;

  public PlayerState(String playerId) {
    this.playerId = playerId;
    this.state = State.IDLE;
    this.consecutiveLosses = 0;
    this.highRiskEventsRemaining = 0;
  }

  public String getPlayerId() {
    return playerId;
  }

  public State getState() {
    return state;
  }

  public void setState(State state) {
    this.state = state;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void startSession() {
    this.state = State.SESSION_STARTED;
    this.sessionId = UUID.randomUUID().toString();
    this.sessionStartTime = System.currentTimeMillis();
  }

  public void startBetting() {
    this.state = State.BETTING;
  }

  public void endSession() {
    this.state = State.SESSION_ENDED;
  }

  public void resetToIdle() {
    this.state = State.IDLE;
    this.sessionId = null;
  }

  public int getConsecutiveLosses() {
    return consecutiveLosses;
  }

  public void recordLoss() {
    consecutiveLosses++;
    if (consecutiveLosses >= 5) {
      highRiskEventsRemaining = 10;
    }
  }

  public void recordWin() {
    consecutiveLosses = 0;
  }

  public boolean isHighRisk() {
    return highRiskEventsRemaining > 0;
  }

  public void decrementHighRiskCounter() {
    if (highRiskEventsRemaining > 0) {
      highRiskEventsRemaining--;
    }
  }

  public long getSessionStartTime() {
    return sessionStartTime;
  }
}
