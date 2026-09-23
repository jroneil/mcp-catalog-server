# MCP Catalog Platform

Slices 1–3: Java 21 Spring Boot backend, PostgreSQL, Flyway migrations, seeded catalog persistence and Actuator health. Catalog search and detail are available through the application service; MCP and REST endpoints are not implemented yet. License: TBD before public distribution.

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

The context test provisions its own PostgreSQL 18.6 container and verifies JDBC plus HTTP health/readiness; it needs no `.env` and never uses H2. Docker image builds compile/package but skip test execution; run the Maven gate separately. Flyway creates and seeds the catalog table. Additional PostgreSQL tests verify migrations, repository mappings, database constraints and failure of application startup on an invalid migration.

For host execution, supply `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` for a reachable PostgreSQL instance, then run `mvn -f backend/pom.xml spring-boot:run`. Host execution binds `127.0.0.1:8080` by default; `.env` is read by Compose, not automatically by Spring Boot. Compose deliberately does not publish PostgreSQL.

See [architecture and exact version decisions](docs/ARCHITECTURE.md) and the [implementation plan](docs/MCP_Catalog_Platform_IMPLEMENTATION_PLAN_v0.1.md). Intended future MCP path: `/mcp`, synchronous Streamable HTTP; it remains absent. Slice 4 has not been started.

## Catalog database (Slice 2)

On startup, Flyway 12.4.0 applies `V1__create_catalog_item.sql` and `V2__seed_catalog_items.sql` to `public`. There are 24 seeded records: 12 products, 12 services, 18 active and 6 inactive. The persistence package uses Spring Data JDBC with read-only ID/SKU lookup and count. There is no catalog HTTP endpoint yet; application lookup behavior goes through CatalogService.

Inspect through the private PostgreSQL container:

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version, description, success FROM public.flyway_schema_history ORDER BY installed_rank;"'
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT type, active, count(*) FROM public.catalog_item GROUP BY type, active ORDER BY type, active;"'
```

Applied migrations are immutable: add a new versioned migration for changes. Restarting on an existing volume validates history without reseeding. Migration failure prevents startup; do not bypass it with auto-DDL, Flyway repair or edited migration checksums. For a deliberate fresh local verification, `docker compose down -v` deletes the local database; then `docker compose up --build --wait --wait-timeout 180` recreates and migrates it.

See [Slice 2 validation](docs/SLICE_2_VALIDATION.md) for exact checks and results. No manual database preparation or external AI service is needed for tests.

## Catalog application service (Slice 3)

`CatalogService.search(CatalogSearchCriteria)` supports optional type, active status, maximum price and literal case-insensitive text matching across SKU/name/description. It returns an immutable page with items and totals. Defaults are page 0 / size 20; size is limited to 100, and results are ordered by unique ID ascending. `CatalogService.getItem(Long)` returns a mapped application result or an explicit not-found exception. There are no write operations.

Validation bounds and precise text/price semantics are documented in [architecture](docs/ARCHITECTURE.md). Run the same Maven `clean verify` command above for service unit tests, real PostgreSQL service/filter/pagination tests, and all earlier regression tests. No AI provider or manual database setup is needed. See [Slice 3 validation](docs/SLICE_3_VALIDATION.md) for gate results.
