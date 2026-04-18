package com.entain.betsafe.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Inbound event request DTO — validated at controller layer.
 */
public record EventRequest(
    @NotBlank(message = "event_id must not be blank")
    String eventId,

    @NotBlank(message = "event_type must not be blank")
    String eventType,

    @NotBlank(message = "player_id must not be blank")
    String playerId,

    @NotBlank(message = "tenant_id must not be blank")
    String tenantId,

    @NotBlank(message = "brand_id must not be blank")
    String brandId,

    @NotNull(message = "client_timestamp must not be null")
    Long clientTimestamp,

    @NotNull(message = "payload must not be null")
    Map<String, Object> payload
) {
}
