# MCP Catalog Platform

Slice 1: Java 21 Spring Boot backend, PostgreSQL and Actuator health. Catalog behavior, Flyway migrations and MCP endpoints are not implemented yet. License: TBD before public distribution.

## Start locally

Requires Docker Engine/Desktop with Compose v2+ (including `--wait`).

1. Copy `.env.example` to `.env` and replace all placeholders with local database settings. Use a generated password (for example, `openssl rand -hex 24`). `.env` is ignored; never commit credentials.
2. Run from the repository root:

```bash
docker compose config --quiet
docker compose up --build --wait --wait-timeout 180
docker compose ps
curl --fail http://127.0.0.1:8080/actuator/health
curl --fail http://127.0.0.1:8080/actuator/health/readiness
docker compose port mcp-catalog-server 8080
```

Both health endpoints report `"status":"UP"` (the aggregate endpoint also lists health groups) after PostgreSQL is usable. The port command must show `127.0.0.1:8080` (or your `BACKEND_PORT`). PostgreSQL is private to the Compose network; backend publication is loopback-only. The container's internal wildcard listener supports Docker forwarding and does not change host binding. Phase 1 is local development, not production-hardened.

`docker compose up --build` also works after configuring `.env`; it attaches logs. Stop with `docker compose down`; the database volume is retained. Changing PostgreSQL credentials in `.env` does not change an already initialized database. For disposable local data only, `docker compose down --volumes` deletes the database and permits fresh initialization.

## Build and test

Host prerequisites: Java 21, Maven 3.9+, running Docker daemon accessible to Testcontainers.

```bash
mvn -f backend/pom.xml --batch-mode --no-transfer-progress clean verify
docker build -t mcp-catalog-server:0.1.0 backend
```

The context test provisions its own PostgreSQL 18.6 container and verifies JDBC plus HTTP health/readiness; it needs no `.env` and never uses H2. Docker image builds compile/package but skip test execution; run the Maven gate separately. No database schema is created.

For host execution, supply `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` for a reachable PostgreSQL instance, then run `mvn -f backend/pom.xml spring-boot:run`. Host execution binds `127.0.0.1:8080` by default; `.env` is read by Compose, not automatically by Spring Boot. Compose deliberately does not publish PostgreSQL.

See [architecture and exact version decisions](docs/ARCHITECTURE.md) and the [implementation plan](docs/MCP_Catalog_Platform_IMPLEMENTATION_PLAN_v0.1.md). Intended future MCP path: `/mcp`, synchronous Streamable HTTP; it is absent in Slice 1. Slice 2 has not been started.
