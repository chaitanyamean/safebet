# BetSafe Intelligence Platform — Phase 1

Event ingestion pipeline for responsible gambling intelligence.

## Quick Start

```bash
# 1. Copy environment file
cp infra/.env.example infra/.env

# 2. Build the project
mvn clean package -DskipTests

# 3. Start all services
cd infra && docker compose up -d

# 4. Verify health
curl http://localhost:8090/actuator/health
```

## Service Ports

| Service          | Port  | URL                          |
|------------------|-------|------------------------------|
| Gateway API      | 8090  | http://localhost:8090        |
| Kafka            | 9092  | -                            |
| Schema Registry  | 8081  | http://localhost:8081        |
| Kafka UI         | 8080  | http://localhost:8080        |
| PostgreSQL       | 5432  | -                            |
| pgAdmin          | 5050  | http://localhost:5050        |
| MinIO API        | 9000  | http://localhost:9000        |
| MinIO Console    | 9001  | http://localhost:9001        |
| Redis            | 6379  | -                            |
| Prometheus       | 9090  | http://localhost:9090        |
| Grafana          | 3000  | http://localhost:3000        |

## Default Credentials

| Service   | Username              | Password         |
|-----------|-----------------------|------------------|
| pgAdmin   | admin@betsafe.local   | pgadmin_secret   |
| MinIO     | minioadmin            | minioadmin_secret |
| Grafana   | admin                 | grafana_secret   |
| PostgreSQL| betsafe               | betsafe_secret   |

## API Usage

### Send an event
```bash
curl -X POST http://localhost:8090/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "550e8400-e29b-41d4-a716-446655440000",
    "eventType": "BET",
    "playerId": "player-123",
    "tenantId": "ladbrokes",
    "brandId": "ladbrokes-uk",
    "clientTimestamp": '$(date +%s000)',
    "payload": {
      "bet_id": "bet-001",
      "market_id": "market-100",
      "stake": "25.00",
      "currency": "GBP",
      "game_type": "SPORTS",
      "odds": "2.50",
      "is_in_play": false
    }
  }'
```

## Run Simulator

```bash
java -jar betsafe-simulator/target/betsafe-simulator-1.0.0-SNAPSHOT.jar \
  --eps 50 --player-count 100 --tenant-ids ladbrokes,coral
```

## Clean Up

```bash
cd infra && docker compose down -v
```
