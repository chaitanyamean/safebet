-- V1: Create event_ledger table — append-only audit ledger
-- player_id is NEVER stored — only player_id_hash (SHA-256)

CREATE TABLE event_ledger (
  id                      BIGSERIAL PRIMARY KEY,
  event_id                UUID NOT NULL UNIQUE,
  event_type              VARCHAR(50) NOT NULL,
  player_id_hash          VARCHAR(64) NOT NULL,
  tenant_id               VARCHAR(100) NOT NULL,
  brand_id                VARCHAR(100) NOT NULL,
  client_timestamp        TIMESTAMPTZ NOT NULL,
  server_ingest_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  kafka_topic             VARCHAR(200),
  kafka_partition         INTEGER,
  kafka_offset            BIGINT,
  schema_version          INTEGER NOT NULL DEFAULT 1,
  payload                 JSONB NOT NULL,
  row_hash                VARCHAR(64),
  created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Required indexes
CREATE INDEX idx_ledger_player ON event_ledger(player_id_hash, created_at DESC);
CREATE INDEX idx_ledger_tenant ON event_ledger(tenant_id, created_at DESC);
CREATE INDEX idx_ledger_event_type ON event_ledger(event_type, created_at DESC);
