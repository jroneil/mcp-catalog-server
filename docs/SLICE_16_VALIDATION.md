# Slice 16 — Natural-Language Catalog Workflow in Angular

Executed 2026-09-25. **Local-only implementation and validation complete.** Hosted
Slice 15 acceptance remains **on hold**; no hosted-provider acceptance claim is made.
Slice 17 has not started. No commits were made.

## Objective and governing requirements

Implement the Angular portion of implementation plan v0.2 Slice 16: one natural-language
catalog request through the existing assistant REST endpoint, readable grounded answer
and returned catalog items, loading and safe failure presentation. Addresses the local UI
portion of FR-13, the FR-12 local path and FR-13A browser boundary.

The user's explicit Slice 16 authorization resolves D9's local UI scenario and permits
local-only execution while Slice 15 hosted acceptance is on hold. The plan's original
both-provider acceptance remains an outstanding Phase 2 obligation, not a waived or
completed requirement. No hosted calls, credentials investigation or provider changes
were performed. Existing uncommitted Slice 15 work was preserved.

## Files changed

Added:

- `frontend/src/app/catalog-assistant-api.ts` — existing response shape and relative POST client.
- `frontend/src/app/catalog-assistant.ts` — standalone single-request interaction state.
- `frontend/src/app/catalog-assistant.html` — prompt, answer, items and safe states.
- `frontend/src/app/catalog-assistant.css` — responsive styling matching the catalog.
- `frontend/src/app/catalog-assistant.spec.ts` — 19 frontend cases.
- `frontend/scripts/catalog-assistant-smoke.mjs` — real local-only browser acceptance.
- `docs/SLICE_16_VALIDATION.md` — this record.

Modified:

- `frontend/src/app/catalog-search.ts` and `.html` — import/embed the assistant below existing results.
- `README.md`, `docs/ARCHITECTURE.md`, `docs/TEST_PLAN.md`, implementation plan v0.2 — local UI usage, boundaries, D9 resolution, status and evidence.

No backend, dependency/lockfile, migration, Docker, security or historical validation
files were changed by this slice. A SHA-256 snapshot taken before editing confirmed all
backend source/configuration files, Compose, `.env.example` and Slice 15 validation were
unchanged afterward. Their preexisting working-tree changes belong to Slice 15.

## Architecture and behavior

Browser → relative `POST /api/v1/catalog/assistant` → existing CatalogAssistantService
→ local Ollama → existing `search_catalog` capability → CatalogService → PostgreSQL.
The existing nginx/development proxy is unchanged. The browser sends only `{prompt}`;
it never calls a provider or MCP directly and contains no provider configuration/secret.

Prompt validation and interpretation remain server-owned. Blank/oversized inputs receive
the backend's safe 400; unsupported intent is also shown for correction. Other failures
use fixed safe messages: unavailable/network (503/502/0), timeout (504), unexpected (500).
Transient errors offer explicit retry of the last submitted prompt; there is no automatic
retry. New submissions replace results and unsubscribe stale requests. Leaving the page
cancels the browser subscription; already-running server inference may continue.

The answer is escaped plain text (model Markdown remains literal); items, order and totals
are authoritative backend data. Links use the existing detail route. Provider/model metadata
is not shown. There is no conversation history, streaming, sessions, RAG, write/admin
operation, prompt interpretation in Angular, provider selection UI or speculative abstraction.

## Tests and exact commands

Host tooling used the already approved Node/npm installation:

```bash
export PATH=/tmp/node-v22.22.3-linux-x64/bin:$PATH
cd frontend
npm test -- --include='src/app/catalog-assistant.spec.ts'
npm test
npm run build
cd ..
mvn -f backend/pom.xml --batch-mode --no-transfer-progress clean verify
AI_PROVIDER=ollama OLLAMA_MODEL=qwen3-coder-next:latest docker compose config --quiet
AI_PROVIDER=ollama OLLAMA_MODEL=qwen3-coder-next:latest docker compose up --build --wait --wait-timeout 300
docker compose ps
curl --fail --silent http://127.0.0.1:8080/actuator/health
docker compose exec -T mcp-catalog-server printenv AI_PROVIDER OLLAMA_MODEL
docker compose port frontend 8080
docker compose port mcp-catalog-server 8080
docker inspect mcp-catalog-server-postgres-1 --format '{{json .HostConfig.PortBindings}}'
curl --silent --output /dev/null --write-out 'MCP untrusted origin: %{http_code}\n' -X POST http://127.0.0.1:8080/mcp -H 'Origin: http://evil.example' -H 'Content-Type: application/json' -d '{}'
cd frontend
CHROME_BIN=/usr/bin/google-chrome npm run smoke
CHROME_BIN=/usr/bin/google-chrome SMOKE_ASSISTANT_SCREENSHOT=/tmp/slice16-assistant.png SMOKE_ASSISTANT_MOBILE_SCREENSHOT=/tmp/slice16-assistant-mobile.png SMOKE_ASSISTANT_EVIDENCE=/tmp/slice16-assistant-evidence.json node scripts/catalog-assistant-smoke.mjs
cd ..
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT id, sku, type, active, price FROM public.catalog_item WHERE type = '\''SERVICE'\'' AND active = true AND price < 200 ORDER BY id;" -c "SELECT version, success FROM public.flyway_schema_history ORDER BY installed_rank;"'
git diff --check
```

| Gate | Result |
| --- | --- |
| Targeted assistant tests | 19 passed |
| Full frontend suite | 59 passed: all 40 existing cases plus 19 new; four test files |
| Angular production build | Passed; initial bundle 285.05 kB, estimated transfer 77.95 kB; no budget warning |
| Full Maven clean verify | BUILD SUCCESS; 285 tests, 0 failures, 0 errors, 0 skipped |
| PostgreSQL/Testcontainers and Phase 1/REST/MCP regressions | Passed in full Maven gate |
| Compose config/build/start | Passed; frontend/backend/PostgreSQL healthy |
| Actuator health | HTTP 200, status UP |
| Host ports | Frontend 127.0.0.1:4200; backend 127.0.0.1:8080 |
| PostgreSQL host port bindings | `{}`; unpublished |
| Flyway | Existing versions 1 and 2 both successful; no schema change |
| MCP hostile Origin | HTTP 403, unchanged protection |
| Existing search/detail browser smoke | Passed, including filters, paging, empty/validation, offline retry, direct detail, history and return |
| Local assistant browser smoke | Passed, actual inference, expected records, same-origin requests, mobile layout and detail/return |
| Patch formatting / protected-file hashes | Passed |

New tests cover prompt-only request mapping, submission/loading, grounded answer/item
rendering/detail links, escaped HTML, server ordering/totals/inactive items, empty results,
blank/whitespace/overlong prompt errors, unsupported-intent 400 and correction, malformed
400, sanitized 503/504/500/502 without diagnostic URL/credential leakage, network failure,
explicit retry, stale/repeated requests and destruction cancellation. Existing search/detail
tests were not weakened or modified. Automated backend tests do not contact live hosted
providers and do not establish hosted acceptance.

## Real browser → REST → local Ollama → PostgreSQL evidence

Prompt: **Show me active service items under $200.**

```text
POST /api/v1/catalog/assistant
request: {"prompt":"Show me active service items under $200."}
HTTP: 200
provider: ollama
model: qwen3-coder-next:latest
capability: search_catalog
arguments: {"type":"SERVICE","active":true,"maxPrice":200}
page: 0; pageSize: 20; totalItems: 6; totalPages: 1
```

The answer listed Workstation Setup, Remote Troubleshooting Session, Device Recycling
Collection, Network Health Assessment, Printer Installation and Initial Technology
Consultation with their persisted descriptions/prices. The browser rendered that exact
answer as text and all six returned cards. Independent SQL confirmed:

| ID | SKU | Price | Type | Active |
| --- | --- | --- | --- | --- |
| 13 | SVC-101 | 149.00 | SERVICE | true |
| 14 | SVC-102 | 85.00 | SERVICE | true |
| 15 | SVC-103 | 45.00 | SERVICE | true |
| 16 | SVC-104 | 199.00 | SERVICE | true |
| 19 | SVC-107 | 120.00 | SERVICE | true |
| 20 | SVC-108 | 0.00 | SERVICE | true |

The model selected inclusive `maxPrice=200`; no seeded item sits exactly at 200, so both
the expected IDs and the independent strict-under-200 SQL result match. No frontend or
service semantics were changed to influence interpretation. The response's items/page
metadata are supplied by the existing catalog capability, not synthesized in Angular.

Playwright observed only frontend-origin browser requests, no `/mcp` requests and no
browser runtime errors. Following Network Health Assessment opened the existing detail
page; returning restored the conventional catalog. At 390 px viewport width there was
no horizontal overflow. Desktop and mobile screenshots were captured at the `/tmp`
paths above (local validation artifacts, not committed); response JSON is stored alongside
them. These artifacts can be regenerated by the smoke command.

## Limitations and exclusions

Hosted Slice 15 acceptance remains on hold. Slice 16 is validated against the accepted
local Ollama path only; no hosted-provider acceptance claim or full Phase 2 acceptance is
made. The user explicitly authorized this local-only exception to the original both-mode
Slice 16 gate. No unresolved decision blocks the implemented local UI.

No endpoint contract, CatalogService semantics, MCP contract, migration or security default
was changed. No Slice 17 or later-slice scope was added. No commit was made.
