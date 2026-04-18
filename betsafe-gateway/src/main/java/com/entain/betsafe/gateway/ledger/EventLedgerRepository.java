package com.entain.betsafe.gateway.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for the append-only event ledger.
 * Exposes required query methods per AL-FR-009.
 */
@Repository
public interface EventLedgerRepository extends JpaRepository<EventLedgerEntity, Long> {

  boolean existsByEventId(UUID eventId);

  List<EventLedgerEntity> findByPlayerIdHashAndCreatedAtBetween(
      String playerIdHash, OffsetDateTime start, OffsetDateTime end);

  long countByTenantIdAndCreatedAtAfter(String tenantId, OffsetDateTime after);
}
