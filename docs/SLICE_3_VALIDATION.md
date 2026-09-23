# Slice 3 validation

Executed 2026-09-23. **Slice 3 passed; Slice 4 has not been started.**

## Files created/changed

Created under `backend/src/main/java/com/example/mcpcatalog/catalog/application/`:

- `CatalogService.java`: validation/defaults, search orchestration, detail lookup, application mapping.
- `CatalogSearchCriteria.java`: optional application inputs.
- `CatalogItemView.java`: annotation-free item result containing all nine fields.
- `CatalogPage.java`: immutable page items and pagination metadata.
- `InvalidCatalogCriteriaException.java`: invalid application input with field and safe explanation.
- `CatalogItemNotFoundException.java`: explicit missing-item behavior.

Persistence changes under `backend/src/main/java/com/example/mcpcatalog/catalog/persistence/`:

- Modified `CatalogItemRepository.java` to include the search fragment; existing lookup/count methods retained.
- Created `CatalogItemSearch.java`: bounded internal persistence operation and row/count result.
- Created `CatalogItemSearchImpl.java`: parameterized PostgreSQL query mechanics and row mapping.

Created under `backend/src/test/java/com/example/mcpcatalog/catalog/application/`:

- `CatalogServiceTest.java`: 25 unit test cases.
- `CatalogServicePostgresTest.java`: 23 PostgreSQL-backed service cases.

Documentation: updated `docs/ARCHITECTURE.md` and `README.md`; created this file. Original requirements, prior validation records, all existing tests, V1/V2 migrations, application configuration, dependency pins, Dockerfile and Compose files were preserved.

## Application and validation decisions

Catalog callers use `CatalogService.search(criteria)` or `getItem(id)`. Public service models do not expose persistence annotations, Spring Data, MCP or HTTP types. The service owns defaults/validation and converts persistence rows to application results. SQL remains in the repository fragment.

| Input | Bound / default |
| --- | --- |
| criteria | null accepted as all fields omitted |
| type | null or exactly PRODUCT / SERVICE; other case, blank and padded values rejected |
| active | null means either state; true/false filter independently |
| maxPrice | null or inclusive 0–9,999,999,999.99; BigDecimal scale at most 2; no rounding or coercion |
| text | at most 200 UTF-16 code units before trimming; NUL rejected; outer whitespace stripped, blank omitted |
| page | default 0; 0–10,000 inclusive |
| pageSize | default 20; 1–100 inclusive; larger values rejected |
| detail id | non-null positive Long; absent valid ID raises CatalogItemNotFoundException |

Bounds are named constants in `CatalogService`. The price maximum matches the existing NUMERIC(12,2) schema. Text/page bounds conservatively constrain query input and offset work. Extra decimal places are rejected even if trailing zeros (e.g. 1.000). BigDecimal cannot represent NaN/infinity; nonnumeric transport parsing remains future adapter work.

## Search, repository and pagination

All optional filters combine with AND; maximum price is inclusive. Text is a literal, case-insensitive substring of SKU, name or description, using PostgreSQL ILIKE. `%`, `_` and `!` are escaped, and all caller values use bound parameters. No full-text search or SQL assembled from user input.

The new fragment is the minimum search support: one count query and one row query sharing predicates, with mandatory LIMIT/OFFSET. Service limits are always 1–100; offset uses long arithmetic. No unbounded list or unpaged service method exists. Repository code performs query mechanics only; it neither defaults inputs nor defines service validation rules.

Ordering is always `id ASC`. The unique primary key prevents ties and provides deterministic pagination independent of query plans and mutable descriptive fields. Count and rows share a read-only REPEATABLE_READ transaction. Separate calls remain stable for unchanged data; they do not share a cross-request snapshot.

`CatalogPage` includes immutable `items`, `page`, `pageSize`, `totalItems` and `totalPages`. Total pages use ceiling division, or zero for no matches. An in-range page past the last result returns an empty list and the correct total. Detail results include all catalog fields; no fabricated item is returned when missing.

## Test coverage and results

Maven `clean verify`: **55 tests, 0 failures, 0 errors, 0 skips**.

| Suite | Cases | Result |
| --- | ---: | --- |
| CatalogServiceTest | 25 | PASS |
| CatalogServicePostgresTest | 23 | PASS |
| CatalogPersistenceTest (Slice 2) | 5 | PASS |
| FlywayStartupFailureTest (Slice 2) | 1 | PASS |
| McpCatalogApplicationTest (Slice 1) | 1 | PASS |

Service unit cases verify defaults, blank text normalization, validated filter delegation, offset calculation, total-page calculation, upper bounds, invalid enum values, negative/oversized/excess-scale prices, long/NUL text, negative/oversized pages, zero/negative/oversized page sizes, invalid detail IDs, and explicit not-found behavior. Invalid input does not invoke repository methods.

Service PostgreSQL cases verify:

- No-filter search, default page 0 and size 20, total 24, two pages.
- PRODUCT and SERVICE individually, active=true and active=false individually.
- Inclusive maximum price, zero price and combinations including all filters.
- Case-insensitive SKU/name/description matching and whitespace normalization.
- Literal wildcard/escape handling and SQL-shaped text remaining data.
- Deterministic repeated pagination without overlaps/gaps across all 24 seeds; correct totals for empty/out-of-range results.
- Page size 100 limits a 129-row matching dataset to 100; default still limits to 20; second size-100 page returns 29. Extra rows are test-only and rolled back.
- Immutable result items, exact detail mapping and missing-item behavior through the service.

PostgreSQL/Testcontainers exercises the real query path without external AI, MCP, Ollama, H2 or manual DB preparation. Earlier tests continue to prove fresh Flyway application, seed retrieval, schema constraints and failure of startup on a bad migration. The intentional negative-test exception in logs is expected.

## Commands run

```bash
# Capture the baseline before editing, then verify it after implementation:
sha256sum backend/src/main/resources/db/migration/*.sql docker-compose.yml backend/Dockerfile > /tmp/mcp-slice3-baseline.sha256
mvn -f backend/pom.xml --batch-mode --no-transfer-progress clean verify
sha256sum -c /tmp/mcp-slice3-baseline.sha256
docker compose config --quiet
docker compose up --build --wait --wait-timeout 180
docker compose ps
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/readiness
docker compose port mcp-catalog-server 8080
docker inspect $(docker compose ps -q) --format '{{.Name}} health={{.State.Health.Status}} ports={{json .NetworkSettings.Ports}}'
docker compose logs --no-color mcp-catalog-server | rg 'Successfully validated|up to date|Current version|Started McpCatalog'
docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
SELECT version, description, checksum, success FROM public.flyway_schema_history ORDER BY installed_rank;
SELECT count(*) FROM public.catalog_item;
SQL
```

## Docker, Flyway and health regression results

- Compose configuration and backend image rebuild passed; `up --build --wait` exited successfully. Both services healthy.
- Existing database volume preserved. Flyway validated both migrations, found schema version 2 and reported no migration necessary.
- History: V1 checksum `162354501`, success=true; V2 checksum `-2124124441`, success=true. Database still contains exactly 24 seed rows.
- Both migration files, Dockerfile and Compose file match their pre-change SHA-256 checksums. Fresh migration execution also passed in the Testcontainers suites.
- Aggregate health: HTTP 200, `{"groups":["liveness","readiness"],"status":"UP"}`.
- Readiness: HTTP 200, `{"status":"UP"}`.
- Actual Docker mapping: backend `8080/tcp` -> HostIp `127.0.0.1`, HostPort `8080`; PostgreSQL `5432/tcp` -> null (not published).
- Docker build retains the prior test-skip behavior; the separate Maven gate executed every test.

Ephemeral logs: `/tmp/mcp-catalog-slice3-maven.log`, `/tmp/mcp-catalog-slice3-compose.log`; test reports in `backend/target/surefire-reports/`.

## Deviations and unresolved risks/questions

No PRD/plan deviation or unresolved Slice 3 blocker. No schema change, dependency upgrade, network change, write operation, full-text search, MCP wiring/tools, REST controller or other excluded feature was added. No rollback was needed.

Offset pagination can become expensive toward the configured cap and separate pages could shift if future writers modify data. These limitations do not require broader features for the current read-only reference catalog. Existing image-tag and local credential/deployment limitations remain as documented. Future adapters must use CatalogService and map its application errors to their protocol intentionally.

The local stack remains running for review. **Slice 4 remains unstarted.**
