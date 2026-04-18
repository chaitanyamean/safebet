package com.entain.betsafe.gateway.dto;

/**
 * Successful event acceptance response.
 */
public record EventResponse(
    String eventId,
    String status,
    Integer kafkaPartition,
    Long kafkaOffset
) {
  public static EventResponse accepted(String eventId, int partition, long offset) {
    return new EventResponse(eventId, "ACCEPTED", partition, offset);
  }
}
