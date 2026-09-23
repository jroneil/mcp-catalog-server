# MCP Catalog Platform

Slices 1–8: Java 21 Spring Boot backend, PostgreSQL, Flyway migrations, seeded catalog persistence, Actuator health, and a Streamable HTTP MCP server exposing the `search_catalog` and `get_catalog_item` tools with request-origin protection. Catalog search and detail are available through the application service and through MCP; REST endpoints are not implemented yet. License: TBD before public distribution.

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

The context test provisions its own PostgreSQL 18.6 container and verifies JDBC plus HTTP health/readiness; it needs no `.env` and never uses H2. Docker image builds compile/package but skip test execution; run the Maven gate separately. Flyway creates and seeds the catalog table. Additional PostgreSQL tests verify migrations, repository mappings, database constraints and failure of application startup on an invalid migration. The Slice 4 suites start the real MCP server on a random port and verify server identity, advertised capabilities, the `/mcp` route, Origin/Host rejection and tool discovery. The Slice 5 and 6 suites verify both tool contracts, mapping, validation and error behavior, and drive the production tools with a real MCP client against the seeded catalog. Slice 7 adds an end-to-end acceptance suite over the whole MCP path, architecture-boundary checks and failure-path sanitization coverage. The full gate currently runs 184 tests with no failures.

For host execution, supply `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` for a reachable PostgreSQL instance, then run `mvn -f backend/pom.xml spring-boot:run`. Host execution binds `127.0.0.1:8080` by default; `.env` is read by Compose, not automatically by Spring Boot. Compose deliberately does not publish PostgreSQL.

See [architecture and exact version decisions](docs/ARCHITECTURE.md) and the [implementation plan](docs/MCP_Catalog_Platform_IMPLEMENTATION_PLAN_v0.1.md). The MCP endpoint is `/mcp` (synchronous Streamable HTTP) and exposes `search_catalog` and `get_catalog_item`; see the MCP section below. Slice 8 has not been started.

## MCP server and tools (Slices 4–7)

The backend runs a Spring AI 2.0.1 / MCP Java SDK 2.0.0 synchronous Streamable HTTP server on the same loopback port:

```text
http://127.0.0.1:8080/mcp      (HTTP GET/POST/DELETE)
```

Identity is `mcp-catalog-server` version `0.1.0`. Only the tool capability is advertised; resources, prompts, completions and the STDIO transport are deliberately absent. Tool registration uses Spring AI `ToolCallback`/`ToolCallbackProvider` beans. Two tools are registered:

### `search_catalog`

Returns one page of catalog items matching optional filters, backed by `CatalogService` and PostgreSQL. All inputs are optional and combine as AND filters:

| Input | Type | Meaning |
| --- | --- | --- |
| `type` | string | `PRODUCT` or `SERVICE` (exact, case-sensitive) |
| `active` | boolean | `true` active only, `false` inactive only |
| `maxPrice` | number | inclusive ceiling, 0–9999999999.99, ≤ 2 decimals |
| `text` | string | case-insensitive literal substring of SKU, name or description, ≤ 200 chars |
| `page` | integer | zero-based, default 0, 0–10000 |
| `pageSize` | integer | default 20, 1–100 |

The result is a JSON document in the tool result's text content:

```json
{"items":[{"id":16,"sku":"SVC-104","name":"Network Health Assessment","type":"SERVICE",
"description":"Review office network configuration and provide a prioritized findings report.",
"price":199.00,"active":true,"createdAt":"2026-01-15T09:00:00Z","updatedAt":"2026-01-15T09:00:00Z"}],
"page":0,"pageSize":20,"totalItems":9,"totalPages":1}
```

Invalid input returns an MCP tool error (`isError: true`) carrying the `CatalogService` message, for example `Page size must be between 1 and 100`. Bounds are deliberately not duplicated in the JSON schema, so `CatalogService` remains the single validating authority. An unexpected internal failure is sanitized at the MCP boundary and returns only `The tool failed due to an internal server error and returned no data.` — never SQL text, a connection string, a file path, a credential or a stack trace.

### `get_catalog_item`

Returns exactly one catalog item by identifier, backed by `CatalogService.getItem(Long)` and PostgreSQL.

| Input | Type | Meaning |
| --- | --- | --- |
| `id` | integer (required) | positive catalog item identifier, for example `16` |

The result is the item object itself, in the same text content, with the same field set, price scale and timestamps as one `search_catalog` item:

```json
{"id":16,"sku":"SVC-104","name":"Network Health Assessment","type":"SERVICE",
"description":"Review office network configuration and provide a prioritized findings report.",
"price":199.00,"active":true,"createdAt":"2026-01-15T09:00:00Z","updatedAt":"2026-01-15T09:00:00Z"}
```

Errors are explicit and never fabricate an item: an unknown identifier returns `isError: true` with `Catalog item not found: 99999`, a non-positive identifier returns `Catalog item ID must be positive`, and a missing or non-integer `id` is rejected by schema validation. Inactive rows are retrievable by identifier, exactly as `CatalogService.getItem` behaves. Unexpected internal failures use the same sanitized message as `search_catalog`.

The transport enforces request-origin protection before any JSON-RPC handling:

| Request | Result |
| --- | --- |
| No `Origin` header (non-browser MCP clients) | allowed |
| `Origin` on a loopback host, any port | allowed |
| `Origin` not in the allowlist | HTTP 403 `Invalid Origin header` |
| `Host` not in the allowlist | HTTP 421 `Invalid Host header` |

Defaults are loopback-only: `mcp.server.security.allowed-origins` = `http://127.0.0.1:*`, `http://localhost:*` and `mcp.server.security.allowed-hosts` = `127.0.0.1:*`, `localhost:*`. Override with `MCP_SERVER_SECURITY_ALLOWED_ORIGINS` / `MCP_SERVER_SECURITY_ALLOWED_HOSTS` (comma-separated). A browser MCP client that sends its own origin must be added explicitly. This protection does not change host exposure: the port is still published only on `127.0.0.1` and PostgreSQL stays unpublished.

Verify the live endpoint after `docker compose up --build --wait`:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/mcp          # 400: route present
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://127.0.0.1:8080/mcp \
  -H 'Origin: http://evil.example' -H 'Accept: application/json, text/event-stream' \
  -H 'Content-Type: application/json' -d 'not-json'                          # 403: origin rejected
curl -s -D - -X POST http://127.0.0.1:8080/mcp -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' -H 'Origin: http://127.0.0.1:8080' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl","version":"0.1.0"}}}'
```

The `initialize` response returns `serverInfo` and a `Mcp-Session-Id`. Reuse that header to list tools and to call the tools:

```bash
curl -s -X POST http://127.0.0.1:8080/mcp -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' -H 'Origin: http://127.0.0.1:8080' \
  -H "Mcp-Session-Id: <session>" -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'

curl -s -X POST http://127.0.0.1:8080/mcp -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' -H 'Origin: http://127.0.0.1:8080' \
  -H "Mcp-Session-Id: <session>" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"search_catalog","arguments":{"type":"SERVICE","active":true,"maxPrice":200,"page":0,"pageSize":20}}}'

curl -s -X POST http://127.0.0.1:8080/mcp -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' -H 'Origin: http://127.0.0.1:8080' \
  -H "Mcp-Session-Id: <session>" \
  -d '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"get_catalog_item","arguments":{"id":16}}}'
```

`tools/list` returns exactly two tools. The search call returns the six active services priced at or below 200 (`SVC-101`, `SVC-102`, `SVC-103`, `SVC-104`, `SVC-107`, `SVC-108`); the detail call returns the `SVC-104` row. A real host validation is still outstanding (Slice 9). See [Slice 4 validation](docs/SLICE_4_VALIDATION.md), [Slice 5 validation](docs/SLICE_5_VALIDATION.md), [Slice 6 validation](docs/SLICE_6_VALIDATION.md), [Slice 7 validation](docs/SLICE_7_VALIDATION.md) and [Slice 8 validation](docs/SLICE_8_VALIDATION.md) for exact evidence.

### Connecting MCP Inspector

MCP Inspector 2.7.0 connects to the loopback server with no allowlist change.

```bash
# Web UI (browser): enter http://127.0.0.1:8080/mcp as an HTTP / Streamable HTTP server
npx -y @modelcontextprotocol/inspector@2.7.0 --web \
  --transport http --server-url http://127.0.0.1:8080/mcp

# Headless CLI (same Inspector, non-interactive)
npx -y @modelcontextprotocol/inspector@2.7.0 --cli \
  --server-url http://127.0.0.1:8080/mcp --transport http --method tools/list
npx -y @modelcontextprotocol/inspector@2.7.0 --cli \
  --server-url http://127.0.0.1:8080/mcp --transport http \
  --method tools/call --tool-name search_catalog \
  --tool-args-json '{"type":"SERVICE","active":true,"maxPrice":200,"pageSize":20}'
```

The Inspector web UI talks to a local Inspector proxy that makes the MCP HTTP requests, so it sends no `Origin` header; if a client does send `Origin: http://localhost:6274` it is already accepted by the default `http://localhost:*` entry. Tool errors surface in the UI as `Tool Error` with the message from `CatalogService`. See [Slice 8 validation](docs/SLICE_8_VALIDATION.md).

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
