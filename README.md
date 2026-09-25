# MCP Catalog Platform

Slices 1–10 (Phase 1 complete): Java 21 Spring Boot backend, PostgreSQL, Flyway migrations, seeded catalog persistence, Actuator health, and a Streamable HTTP MCP server exposing the `search_catalog` and `get_catalog_item` tools with request-origin protection. Slice 11 adds read-only REST catalog search and detail through the same CatalogService as MCP. Phase 2 follows implementation plan v0.2; Slice 12 adds Angular search, Slice 13 adds item detail and Slice 14 adds the backend local Ollama catalog assistant; Slice 15 hosted acceptance is on hold; Slice 16 adds the local-only Angular assistant; Slice 17 has not started. License: TBD before public distribution.

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

Open **http://127.0.0.1:4200** for the catalog search UI. The frontend is served by nginx with same-origin REST proxying. `FRONTEND_PORT` can change the host port while retaining loopback binding.

Both health endpoints report `"status":"UP"` (the aggregate endpoint also lists health groups) after PostgreSQL is usable. The port command must show `127.0.0.1:8080` (or your `BACKEND_PORT`). PostgreSQL is private to the Compose network; backend publication is loopback-only. The container's internal wildcard listener supports Docker forwarding and does not change host binding. Phase 1 is local development, not production-hardened.

`docker compose up --build` also works after configuring `.env`; it attaches logs. Stop with `docker compose down`; the database volume is retained. Changing PostgreSQL credentials in `.env` does not change an already initialized database. For disposable local data only, `docker compose down --volumes` deletes the database and permits fresh initialization.

## Build and test

Host prerequisites: Java 21, Maven 3.9+, running Docker daemon accessible to Testcontainers.

```bash
mvn -f backend/pom.xml --batch-mode --no-transfer-progress clean verify
docker build -t mcp-catalog-server:0.1.0 backend
```

The context test provisions its own PostgreSQL 18.6 container and verifies JDBC plus HTTP health/readiness; it needs no `.env` and never uses H2. Docker image builds compile/package but skip test execution; run the Maven gate separately. Flyway creates and seeds the catalog table. Additional PostgreSQL tests verify migrations, repository mappings, database constraints and failure of application startup on an invalid migration. The Slice 4 suites start the real MCP server on a random port and verify server identity, advertised capabilities, the `/mcp` route, Origin/Host rejection and tool discovery. The Slice 5 and 6 suites verify both tool contracts, mapping, validation and error behavior, and drive the production tools with a real MCP client against the seeded catalog. Slice 7 adds an end-to-end acceptance suite over the whole MCP path, architecture-boundary checks and failure-path sanitization coverage. The backend gate currently runs **285 tests** with no failures, including the accepted REST/MCP coverage and existing assistant regressions. The frontend gate runs **59 tests**, including 19 Slice 16 assistant cases, with real-browser search/detail and local assistant smoke checks. Passing deterministic regressions does not imply hosted acceptance.

For host execution, supply `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` for a reachable PostgreSQL instance, then run `mvn -f backend/pom.xml spring-boot:run`. Host execution binds `127.0.0.1:8080` by default; `.env` is read by Compose, not automatically by Spring Boot. Compose deliberately does not publish PostgreSQL.

See [architecture and exact version decisions](docs/ARCHITECTURE.md) and the [implementation plan v0.2](docs/MCP_Catalog_Platform_IMPLEMENTATION_PLAN_v0.2.md). The MCP endpoint is `/mcp` (synchronous Streamable HTTP) and exposes `search_catalog` and `get_catalog_item`; see the MCP section below. Slice 10 (the Phase 1 acceptance audit) is complete — see [Slice 10 validation](docs/SLICE_10_VALIDATION.md); Slice 11 adds the REST adapter; see [Slice 11 validation](docs/SLICE_11_VALIDATION.md). Slice 12 adds Angular search and Slice 13 adds item detail; Slice 14 is accepted, Slice 15 hosted acceptance is on hold, Slice 16 adds the local assistant UI and Slice 17 has not started.

## MCP server and tools (Slices 4–9)

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

`tools/list` returns exactly two tools. The search call returns the six active services priced at or below 200 (`SVC-101`, `SVC-102`, `SVC-103`, `SVC-104`, `SVC-107`, `SVC-108`); the detail call returns the `SVC-104` row. Real-host validation was completed in Slice 9 with opencode and local Ollama; see [Slice 9 validation](docs/SLICE_9_VALIDATION.md). See [Slice 4 validation](docs/SLICE_4_VALIDATION.md), [Slice 5 validation](docs/SLICE_5_VALIDATION.md), [Slice 6 validation](docs/SLICE_6_VALIDATION.md), [Slice 7 validation](docs/SLICE_7_VALIDATION.md) and [Slice 8 validation](docs/SLICE_8_VALIDATION.md) for exact evidence.

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

### Connecting a real MCP host

```bash
claude mcp add --transport http catalog http://127.0.0.1:8080/mcp
claude mcp list
# Checking MCP server health…
# catalog: http://127.0.0.1:8080/mcp (HTTP) - ✔ Connected
```

`claude mcp add` defaults to local scope, so the entry is written to `~/.claude.json`
keyed by the current directory and the repository stays untouched (use `-s user` for all
projects, or `-s project` to commit a `.mcp.json`). No Origin/Host allowlist change is
needed: Claude Code is a non-browser client that sends no `Origin`, and its Host is
loopback. Remove it with `claude mcp remove catalog -s local`. Running natural-language
prompts requires the host to be authenticated (`/login` or `ANTHROPIC_API_KEY`); that
installation has no credentials here, so its prompts were not validated.

Slice 9 completed the real-host gate with **opencode 1.18.23 driving a local Ollama
model**. In a scratch directory (not this repository), create `opencode.json`:

```json
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": { "catalog": { "type": "remote", "url": "http://127.0.0.1:8080/mcp", "enabled": true } }
}
```

then verify and use the tools with a local model:

```bash
opencode mcp list                      # ● ✓ catalog connected
opencode run 'Find all active service items under $200.' -m ollama/qwen3-coder-next:latest
opencode run 'Show me catalog item 16.' -m ollama/qwen3-coder-next:latest
```

The host exposes the tools as `catalog_search_catalog` and `catalog_get_catalog_item`
and answers from the returned data only. `qwen3-coder-next:latest` is the installed
Ollama model whose tool calls Ollama parses correctly; `qwen2.5-coder:14b` returns the
right arguments as plain text instead. See [Slice 9 validation](docs/SLICE_9_VALIDATION.md)
for the full evidence and caveats.

## Catalog database (Slice 2)

On startup, Flyway 12.4.0 applies `V1__create_catalog_item.sql` and `V2__seed_catalog_items.sql` to `public`. There are 24 seeded records: 12 products, 12 services, 18 active and 6 inactive. The persistence package uses Spring Data JDBC with read-only ID/SKU lookup and count. Read-only search and detail are available at `/api/v1/catalog` and `/api/v1/catalog/{id}`; both REST and MCP use CatalogService.

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


## REST catalog (Slice 11)

Both read-only endpoints use the same CatalogService and PostgreSQL data as MCP:

```bash
curl --fail --silent --show-error 'http://127.0.0.1:8080/api/v1/catalog?type=SERVICE&active=true&maxPrice=200'
curl --fail --silent --show-error http://127.0.0.1:8080/api/v1/catalog/16
```

`GET /api/v1/catalog` accepts optional `type`, `active`, `maxPrice`, `text`, `page`,
`pageSize`. It returns HTTP 200 JSON with `items`, `page`, `pageSize`, `totalItems`,
`totalPages`. Existing service rules apply: default page 0/pageSize 20, page 0–10000,
pageSize 1–100, fixed `id ASC` ordering, exact PRODUCT/SERVICE type, inclusive price
0–9999999999.99 with at most two decimal places, and literal case-insensitive text
(up to 200 characters, no NUL). Omitted active includes both states. Filters combine
with AND; text matches SKU, name or description. Blank text means no text filter.

`GET /api/v1/catalog/{id}` returns the nine-field item, including inactive records.
Item timestamps are `createdAt`/`updatedAt`. There are no write endpoints.

Malformed binding or service validation returns 400; missing positive IDs return 404;
unexpected failures return a sanitized 500. Errors contain only `status`, `error`,
`message`, `path`, for example:

```json
{"status":404,"error":"Not Found","message":"Catalog item not found: 99999","path":"/api/v1/catalog/99999"}
```

HTTP parameter parsing uses Spring MVC's existing scalar binding. Business validation
stays in CatalogService. Decimal query text retains its scale (`maxPrice=1.000` is
rejected); the MCP JSON transport may normalize trailing zeros before service validation.
No frontend, CORS configuration or AI-provider integration is included in Slice 11.


## Angular catalog search (Slice 12)

The UI offers text, type, active/inactive and maximum-price filters, optional page size,
readable catalog cards and bounded page navigation. Loading, empty, validation and
backend-unavailable states are distinct. The server owns catalog rules and defaults;
blank controls are omitted and decimal text is not rounded by Angular.

Approved toolchain: Angular core 22.2.0, CLI/build 22.1.8, Node 22.22.3, npm 10.9.8.
Use the checked-in package-lock.json:

```bash
cd frontend
npm ci
npm test
npm run build
npm start
```

`npm start` listens on `127.0.0.1:4200` and proxies `/api` to `127.0.0.1:8080`.
If the Compose frontend occupies port 4200, stop only that service first:
`docker compose stop frontend`. Stop the dev server before restoring the container with
`docker compose up -d --wait frontend`. Both use relative `/api/v1/catalog` requests.
Container production assets are served by nginx 1.30.5-alpine3.24; it proxies `/api/`
to the backend on the Compose network. No CORS configuration is needed or added.
The frontend does not proxy `/mcp`; backend MCP access and its Origin/Host checks remain unchanged.

With the full stack running, run `CHROME_BIN=/usr/bin/google-chrome npm run smoke`
from `frontend/` for the real-browser acceptance check; alternatively install the
Playwright browser with `npx playwright install chromium` and run `npm run smoke`.
See [frontend instructions](frontend/README.md) and [Slice 12 validation](docs/SLICE_12_VALIDATION.md).

Slice 13 adds item detail below; Slice 16 adds the local assistant UI. Writes/admin,
authentication and Phase 3 functionality remain excluded.


## Catalog item detail (Slice 13)

Select a catalog item's linked name to open `/catalog/{id}`, for example
http://127.0.0.1:4200/catalog/16. The page fetches the existing REST detail endpoint
and displays name, description, type, price, status, SKU, ID and timestamps (UTC).
Inactive items remain accessible. Loading, not-found, validation and backend/request
failures have distinct states, with retry for request failures and a **Back to catalog**
link in every state.

Returning within the current tab restores the submitted filters, page and any unsubmitted
form edits, then reloads search results from REST. Browser back/forward also works.
The small search snapshot is in memory only: refreshing or opening a direct detail link
starts a new session, so returning from that session shows the default search.
Direct detail URLs and refresh work through the unchanged nginx SPA fallback.

The existing frontend test/build/smoke commands cover detail as well as search.
See [Slice 13 validation](docs/SLICE_13_VALIDATION.md).

## Local catalog assistant (Slice 14)

`POST /api/v1/catalog/assistant` interprets one bounded natural-language catalog search
request with a local Ollama model, invokes the existing `search_catalog` capability and
returns results grounded in the catalog database. It is a new endpoint; the accepted
`/api/v1/catalog` search and detail endpoints are unchanged.

```bash
curl -s -X POST http://127.0.0.1:8080/api/v1/catalog/assistant \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"Show me active service items under $200."}'
```

The request is `{"prompt": "..."}`: required, trimmed, non-blank and at most 1000
characters, otherwise 400. The response is:

```json
{
  "answer": "…", "capability": "search_catalog", "arguments": { "type": "SERVICE", "active": true, "maxPrice": 199.99 },
  "items": [ … ], "page": 0, "pageSize": 20, "totalItems": 6, "totalPages": 1,
  "provider": "ollama", "model": "qwen3-coder-next:latest"
}
```

`items` and the page metadata come from the catalog capability result, not from the model,
and `arguments` are the arguments actually used. Model-generated arguments pass through the
existing `CatalogService` validation, so invalid values produce the same safe 400 as REST
search. Exactly one capability invocation is permitted per request, and there is no model or
tool retry.

Configuration is environment-backed:

| Variable | Default | Purpose |
| --- | --- | --- |
| `AI_ENABLED` | `true` | Set `false` to disable the assistant (endpoint returns 503) |
| `AI_PROVIDER` | `ollama` | Only `ollama` is implemented in Slice 14 |
| `OLLAMA_BASE_URL` | `http://127.0.0.1:11434` host, `http://host.docker.internal:11434` in Compose | Ollama endpoint |
| `OLLAMA_MODEL` | `qwen3-coder-next:latest` | Local model |
| `AI_TIMEOUT` | `60s` | Bounded provider timeout (504 on expiry) |

Compose passes `OLLAMA_BASE_URL=http://host.docker.internal:11434` and adds the minimal
`extra_hosts: host.docker.internal:host-gateway` mapping so the backend container can reach
a local Ollama that stays outside Compose. No Ollama service is added and PostgreSQL and the
backend keep their existing loopback-only publication.

**The application does not require Ollama to start.** Startup never contacts it and REST,
MCP and catalog behavior work normally without it; only the assistant endpoint reports a
sanitized 503 when the provider is unreachable. A non-`ollama` provider value, or
`AI_ENABLED=false`, disables the assistant without affecting startup. Local mode talks only
to the local Ollama instance and the existing local application/database path; no hosted
provider is configured, no automatic hosted fallback exists and no hosted credential is
required. Errors are sanitized (400 invalid or unsupported request, 503 unavailable, 504
timeout, 500 internal) and never include provider diagnostics, prompts or stack traces.

There is no AI user interface in Slice 14 — Angular AI interaction is Slice 16 and the
hosted provider is Slice 15. See [Slice 14 validation](docs/SLICE_14_VALIDATION.md).

## Ask the catalog (Slice 16, local-only)

On the catalog page, use **Ask the catalog** below the conventional search results.
Enter “Show me active service items under $200.” and select **Find catalog items**.
The panel displays the backend answer and matching catalog records with detail links.
Loading and safe errors are shown; correct a validation/unsupported request or use
**Retry catalog request** for transient failures. A new submission replaces the old
result. This is one catalog interaction, without chat history or provider controls.

Run the accepted local configuration (Ollama remains outside Compose):

```bash
AI_PROVIDER=ollama OLLAMA_MODEL=qwen3-coder-next:latest docker compose up --build --wait --wait-timeout 300
cd frontend
CHROME_BIN=/usr/bin/google-chrome node scripts/catalog-assistant-smoke.mjs
```

The browser calls only the relative backend assistant endpoint through the existing
same-origin proxy. The smoke test checks real local inference, six persisted service
IDs (13, 14, 15, 16, 19, 20), item navigation and mobile layout. Optional
`SMOKE_ASSISTANT_SCREENSHOT`, `SMOKE_ASSISTANT_MOBILE_SCREENSHOT` and
`SMOKE_ASSISTANT_EVIDENCE` paths capture evidence. Existing `npm run smoke` still
checks conventional search/detail independently of AI availability.

Hosted Slice 15 acceptance remains **on hold**. Slice 16 validation is against local
Ollama only; no hosted-provider acceptance is claimed. See
[Slice 16 validation](docs/SLICE_16_VALIDATION.md).
