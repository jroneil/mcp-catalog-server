# Slice 2 validation

Executed 2026-09-23. **Slice 2 passed. Slice 3 has not been started.**

## Files created or changed

Modified:

- `backend/pom.xml`: Spring Data JDBC starter replaces the plain JDBC starter; Flyway Boot starter and PostgreSQL module activated. Existing version pins retained.
- `backend/src/main/resources/application.yml`: explicit Flyway location/schema, validation, no automatic baseline or clean; Spring SQL initialization remains disabled.
- `docs/ARCHITECTURE.md`: current slice status and schema/mapping/repository decisions.
- `README.md`: migration/seed behavior, inspection commands and current scope.

Created:

- `backend/src/main/resources/db/migration/V1__create_catalog_item.sql`
- `backend/src/main/resources/db/migration/V2__seed_catalog_items.sql`
- `backend/src/main/java/com/example/mcpcatalog/catalog/persistence/CatalogItem.java`
- `backend/src/main/java/com/example/mcpcatalog/catalog/persistence/CatalogItemType.java`
- `backend/src/main/java/com/example/mcpcatalog/catalog/persistence/CatalogItemRepository.java`
- `backend/src/test/java/com/example/mcpcatalog/catalog/persistence/CatalogPersistenceTest.java`
- `backend/src/test/java/com/example/mcpcatalog/catalog/persistence/FlywayStartupFailureTest.java`
- `backend/src/test/resources/db/invalid/V3__deliberate_failure.sql` (test-only failure fixture)
- `docs/SLICE_2_VALIDATION.md`

Dockerfile, Compose, health configuration, credentials, original PRD/plan and historical Slice 1 validation were preserved. The existing context/HTTP health test remains unchanged and now starts against a Flyway-initialized database.

## Migrations and schema

V1 creates `public.catalog_item` with all nine required fields. V2 inserts the reference data. Both are immutable once applied; later changes must use new migrations.

| Field | Storage / mapping |
| --- | --- |
| id | BIGINT identity primary key / Long; explicit seed IDs 1–24, next generated ID 25 |
| sku | VARCHAR(64), unique, nonblank, case-sensitive / String |
| name | VARCHAR(200), nonblank / String |
| type | VARCHAR(16), CHECK PRODUCT or SERVICE / CatalogItemType enum |
| description | TEXT, nonblank / String |
| price | NUMERIC(12,2), nonnegative / BigDecimal |
| active | BOOLEAN, default true / boolean |
| created_at | TIMESTAMP WITH TIME ZONE, default current timestamp / OffsetDateTime |
| updated_at | TIMESTAMP WITH TIME ZONE, default current timestamp, >= created_at / OffsetDateTime |

All fields are non-null. The configured PostgreSQL user owns the created objects in the explicitly selected `public` schema. Flyway owns all DDL; no Hibernate, H2, auto-DDL, `schema.sql`, or `data.sql` is present. Startup uses normal Boot Flyway initialization and fails on migration errors.

Timestamp defaults initialize rows; future update code must maintain `updated_at` explicitly. No update API is included. Numeric scale is a storage decision, not a completed service precision-validation contract. No currency conversion behavior has been added.

## Seeds and repository

24 business-oriented rows: 12 products and 12 services, each with 9 active and 3 inactive records (18 active / 6 inactive total). Prices span 0.00–1249.00; descriptions cover office hardware, installation, support, assessments and retired offerings. Fixed IDs and UTC timestamps support reproducible later tests.

Known records verified through repository tests and live SQL:

- ID 1 / PRD-101: Ergonomic Wireless Mouse, PRODUCT, 39.95, active.
- ID 16 / SVC-104: Network Health Assessment, SERVICE, 199.00, active.

Spring Data JDBC 4.1.1 provides the repository implementation. `CatalogItem` is an explicitly annotated immutable persistence record. The narrow repository exposes only `findById`, `findBySku`, and `count`; missing records return empty Optional. No write methods, unbounded listing, search criteria, filtering business logic or service layer is introduced.

## Automated tests

`mvn clean verify` ran **7 tests, 0 failures, 0 errors, 0 skips**, against Testcontainers PostgreSQL 18.6:

1. Existing application context/JDBC and HTTP health/readiness regression test.
2. Flyway V1/V2 applied and validated; no pending migration; rerun executes zero migrations and preserves seed count.
3. All 24 seeded rows retrieve through the repository with correct type/status coverage and valid mapped fields.
4. Deterministic product/service retrieval, exact money/timestamp mapping, missing ID/SKU and case-sensitive SKU lookup.
5. Identity generation after explicit seeds and database timestamp/active defaults (test insert rolled back).
6. PostgreSQL rejects invalid item type, negative price and duplicate SKU.
7. Test-only V3 deliberately fails after creating a probe table: full application startup throws with a Flyway cause, and PostgreSQL rolls back that table.

No external AI/MCP/Ollama service, manual database setup or `.env` is used by the tests. Docker access is required and tests are not silently skipped when it is absent. Test-only V3 is outside the production migration path and excluded from the runtime JAR.

## Commands and results

Run from repository root:

```bash
mvn -f backend/pom.xml --batch-mode --no-transfer-progress clean verify
docker compose down -v
docker compose config --quiet
docker compose up --build --wait --wait-timeout 180
docker compose ps
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/readiness
docker compose port mcp-catalog-server 8080
docker inspect $(docker compose ps -q) --format '{{.Name}} health={{.State.Health.Status}} ports={{json .NetworkSettings.Ports}}'
docker compose logs --no-color mcp-catalog-server | rg 'Migrating schema|Successfully applied|Successfully validated|Started McpCatalog'
docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
SELECT version, description, success FROM public.flyway_schema_history ORDER BY installed_rank;
SELECT type, active, count(*) FROM public.catalog_item GROUP BY type, active ORDER BY type, active;
SELECT count(*), min(price), max(price) FROM public.catalog_item;
SELECT id, sku, name, type, price, active FROM public.catalog_item WHERE id IN (1, 16);
SQL
mvn -f backend/pom.xml --batch-mode --no-transfer-progress dependency:tree '-Dincludes=org.springframework.data:*,org.flywaydb:*,org.hibernate.orm:*,com.h2database:*,org.springframework.ai:*,io.modelcontextprotocol.sdk:*'
jar tf backend/target/mcp-catalog-server-0.1.0.jar | rg 'db/|catalog/persistence|flyway|spring-data'
```

| Gate | Result |
| --- | --- |
| Maven clean verify | PASS, including compilation, all 7 tests and executable JAR packaging |
| PostgreSQL integration tests | PASS on real PostgreSQL 18.6 with Testcontainers 2.0.5 |
| Compose down -v | PASS, prior containers/network/database volume removed as requested |
| Compose config | PASS |
| Compose up --build --wait | PASS, backend image built and fresh database initialized |
| Flyway clean startup | PASS, logs and history show V1 and V2 applied successfully; schema at v2 |
| Seed rows | PASS, 24 rows; PRODUCT false=3/true=9, SERVICE false=3/true=9 |
| Backend and PostgreSQL health | Both healthy |
| Aggregate Actuator health | HTTP 200, `{"groups":["liveness","readiness"],"status":"UP"}` |
| Actuator readiness | HTTP 200, `{"status":"UP"}` |
| Backend publication | `127.0.0.1:8080`; Docker inspection confirms only that HostIp/HostPort |
| PostgreSQL publication | None; Docker inspection reports `5432/tcp: null` |
| Dependencies | Spring Data JDBC/Relational/Commons 4.1.1, Flyway core/PostgreSQL 12.4.0; no Hibernate, H2, AI or MCP runtime dependencies |
| Runtime migration packaging | Only production V1/V2; deliberate-failure fixture excluded |

Existing pins remain Boot 4.1.1, JDBC 42.7.13 and Java 21. Docker builds skip test execution as before; the separate Maven gate ran all tests successfully. Logs include the intentional startup failure from the negative test; it is expected, not a failed gate.

Execution logs: `/tmp/mcp-catalog-slice2-maven.log`, `/tmp/mcp-catalog-slice2-compose.log`, `/tmp/mcp-catalog-slice2-dependencies.log` (ephemeral).

## Deviations, risks and remaining work

No PRD or implementation-plan scope deviation and no unresolved Slice 2 blocker. Spring Data JDBC is the permitted chosen persistence model rather than JPA. No dependency versions or Docker networking rules were changed.

The local reference seed migration always runs for this Phase 1 application; production-specific data/bootstrap policy is outside this slice. The existing local shared migration/runtime credentials and release-tag image pinning limitations remain documented in architecture. Applied migration changes must use a new migration; do not modify V1/V2 to evade validation.

No CatalogService, business search, MCP activation/tools, REST controllers, frontend, model-provider integration, Python, resources, prompts or STDIO was added. Slice 3 remains unstarted. The stack is left running with fresh seeded data on loopback for review.
