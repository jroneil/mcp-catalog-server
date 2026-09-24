# Slice 13 validation — Angular catalog item detail

Executed 2026-09-24. **Slice 13 passes.** Slice 14 has not been started.

This record closes the Slice 13 work that was already present in the working tree. It
reviews that work for correctness and scope, then re-runs every Slice 13 gate from the
existing tree. No code was rewritten; the only changes made here are the documentation
finishing steps (plan status, test-plan coverage row, this record).

## 1. Scope reviewed

Uncommitted Slice 13 changes on top of `8ba53ab feat: add Slice 12 Angular catalog search UI`:

| Status | Path | Role |
| --- | --- | --- |
| added | `frontend/src/app/app.routes.ts` | `/` search, `/catalog/:id` detail, `**` → search |
| added | `frontend/src/app/catalog-detail.ts/.html/.css` | detail component and presentation |
| added | `frontend/src/app/catalog-detail.spec.ts` | 12 routing/state/error cases |
| added | `frontend/src/app/catalog-search.ts/.html/.css` | search UI extracted from the old `App` |
| added | `frontend/src/app/catalog-search.spec.ts` | the 16 accepted search cases (moved) |
| added | `frontend/src/app/catalog-search-state.ts` | in-memory return-navigation snapshot |
| deleted | `frontend/src/app/app.spec.ts` | search cases moved verbatim to `catalog-search.spec.ts` |
| modified | `frontend/src/app/app.ts/.html/.css`, `app.config.ts` | shared shell + `provideRouter` |
| modified | `frontend/src/app/catalog-api.ts` (+spec) | `detail(id)` and 3 new API cases |
| modified | `frontend/package.json`, `package-lock.json` | `@angular/router` 22.2.0 only |
| modified | `frontend/scripts/catalog-smoke.mjs` | Slice 13 browser acceptance steps |
| modified | `frontend/README.md`, `README.md`, `docs/ARCHITECTURE.md`, `docs/TEST_PLAN.md`, `docs/MCP_Catalog_Platform_IMPLEMENTATION_PLAN_v0.2.md` | documentation |

Review findings:

- Search-case coverage moved 1:1 — the 16 `app.spec.ts` case names reappear in
  `catalog-search.spec.ts`; nothing was dropped, and its obsolete single-tool assertion
  was replaced by the real detail link. `catalog-api.spec.ts` gained the detail cases.
- The dependency delta is exactly `@angular/router@22.2.0` (verified in the lockfile
  diff); the `package.json` devDependency lines were only re-sorted and no other lockfile
  version moved.
- `frontend/nginx.conf`, `frontend/Dockerfile`, `frontend/proxy.conf.json` and
  `docker-compose.yml` are untouched, so direct `/catalog/:id` URLs and refresh rely on
  the existing SPA fallback rather than new routing infrastructure.
- `CatalogApi.detail` sends the route ID as one encoded path segment to the existing
  relative `GET /api/v1/catalog/{id}` and leaves parsing/validation to the server; large
  IDs are not coerced into imprecise JavaScript numbers.
- Error handling maps 404 → not-found, a well-formed 400 envelope → the safe server
  message, an unrecognised 400 body → a generic message, and 0/502/503/504 → unavailable;
  raw diagnostic bodies are never rendered.
- `CatalogSearchState` stores only filters/request/draft in memory, never records, never
  writes to browser storage, and every return re-fetches from REST.
- No write capability, no browser database access, no MCP call, no AI/provider or
  Slice 14 work is present.

No conflict with the Slice 13 plan was found, so no architecture change was required.

## 2. Toolchain

| Item | Value |
| --- | --- |
| Node / npm | 22.22.3 / 10.9.8 (approved runtime, `.nvmrc`, `engines`, `node:22.22.3-bookworm-slim`) |
| Node location | `/tmp/node-v22.22.3-linux-x64/bin` (host default `node` is 24.18.0, which does not satisfy the pinned `engines` with `engine-strict=true`) |
| Angular | core/compiler 22.2.0; CLI/build 22.1.8 |
| Browser for smoke | `/usr/bin/google-chrome`, Playwright 1.63.0 |

## 3. Commands run and results

### 3.1 Slice 13 frontend tests

```bash
cd frontend
export PATH=/tmp/node-v22.22.3-linux-x64/bin:$PATH
npx ng test --watch=false --list-tests                      # 3 spec files discovered
npx ng test --watch=false --include src/app/catalog-detail.spec.ts
```

```text
 ✓ |catalog-frontend| src/app/catalog-detail.spec.ts (12 tests) 418ms
 Test Files  1 passed (1)      Tests  12 passed (12)
```

Covers: link navigation with loading state, all nine displayed fields, direct URL,
inactive item, 404 without fabricated data, return to catalog, 400/500/502/503 handling
with retry and no diagnostic leakage, network failure, unrecognised error envelope,
route-change cancellation and stale-data clearing, cancellation on leave, and restoration
of submitted filters/page with unsubmitted draft preserved.

### 3.2 Full frontend suite

```bash
cd frontend && npm test          # ng test --watch=false
```

```text
 ✓ src/app/catalog-api.spec.ts    (12 tests) 43ms
 ✓ src/app/catalog-search.spec.ts (16 tests) 420ms
 ✓ src/app/catalog-detail.spec.ts (12 tests) 470ms
 Test Files  3 passed (3)      Tests  40 passed (40)
```

### 3.3 Angular production build

```bash
cd frontend && npm run build     # ng build --configuration production
```

```text
 Initial total  279.71 kB  (estimated transfer 76.94 kB)
 Application bundle generation complete. [2.354 seconds]
 Output location: frontend/dist/catalog-frontend
```

Output present: `dist/catalog-frontend/browser/{index.html,main-3HW53AX6.js,styles-BK44HSB5.css}`
— the path the Dockerfile copies from.

### 3.4 Full backend regression gate

```bash
mvn -f backend/pom.xml --batch-mode --no-transfer-progress clean verify
```

```text
 BUILD SUCCESS — Tests run: 232, Failures: 0, Errors: 0, Skipped: 0
```

Identical to the Slice 12 recorded baseline (232), so no backend regression and no
backend change was needed.

### 3.5 Docker Compose and health

```bash
docker compose up --build --wait --wait-timeout 300
docker compose ps
curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/actuator/health
curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:4200/
```

```text
 frontend            Up (healthy)  127.0.0.1:4200->8080/tcp
 mcp-catalog-server  Up (healthy)  127.0.0.1:8080->8080/tcp
 postgres            Up (healthy)  5432/tcp
 backend health HTTP 200 ; readiness HTTP 200 ; frontend HTTP 200
```

### 3.6 Required browser smoke validation

```bash
cd /data/projects/mcp-catalog-server
export PATH=/tmp/node-v22.22.3-linux-x64/bin:$PATH
CHROME_BIN=/usr/bin/google-chrome \
  SMOKE_SCREENSHOT=/tmp/slice13-search.png \
  SMOKE_MOBILE_SCREENSHOT=/tmp/slice13-search-mobile.png \
  SMOKE_DETAIL_SCREENSHOT=/tmp/slice13-detail.png \
  SMOKE_DETAIL_MOBILE_SCREENSHOT=/tmp/slice13-detail-mobile.png \
  npm --prefix frontend run smoke
```

```text
 Real catalog search: 6 active services <= 200; IDs 13,14,15,16,19,20.
 PASS: detail links, all fields, inactive direct URL/refresh, 404, offline failure/retry,
       browser back/forward, restored search and return navigation.
 PASS: default search, page navigation, combined filters, rendering, empty state,
       validation, mobile layout, same-origin requests; no browser runtime errors.
```

Required flows, all against the real Compose stack, real REST and real PostgreSQL seed
data (no mocks):

| Required flow | Evidence |
| --- | --- |
| search → detail navigation | Clicked the `Network Health Assessment` result link, URL became `/catalog/16`, heading rendered |
| known active item | `SVC-104`, price `199.00`, both timestamps `2026-01-15T09:00:00Z`, `Active`, and a real `GET /api/v1/catalog/16` was observed |
| known inactive item | Direct `/catalog/22` and a browser reload rendered `Legacy Email Account Setup` with `Inactive`, with only `/api/v1/catalog/22` requested |
| missing item | `/catalog/99999` rendered `Item not found`, with no `.detail-card` present |
| return to search with filters/page restored | Browser back returned to the filtered 6-card result; browser forward re-opened the detail; `Back to catalog` restored `Item type = SERVICE`, `Availability = true`, `Maximum price = 200` and `Page 1 of 1` |
| request failure + retry | Real browser offline mode produced the unavailable alert with no detail card; going back online and pressing `Try again` rendered the item |
| no fabricated data / same origin | Every `/api/` request was same-origin; the smoke asserts zero browser runtime errors |

Screenshots captured locally: `/tmp/slice13-detail.png`, `/tmp/slice13-detail-mobile.png`,
`/tmp/slice13-search.png`, `/tmp/slice13-search-mobile.png`. The detail page renders
`SERVICE · SVC-104`, the name, description, `Price 199.00`, Catalog ID 16, SKU, Type,
Availability, and both UTC timestamps, with no horizontal overflow at 390 px.

## 4. Unchanged behaviour confirmed

| Area | Check | Result |
| --- | --- | --- |
| Backend / REST / MCP / migrations | working tree contains no `backend/`, `docker-compose.yml` or migration change | confirmed |
| MCP behaviour | `tools/list` → `[get_catalog_item, search_catalog]` | unchanged |
| REST detail contract | `GET /api/v1/catalog/16` → 200 `SVC-104 199.00`; `/99999` → 404 `Catalog item not found: 99999`; `/0` → 400 `Catalog item ID must be positive` | unchanged envelope `{status,error,message,path}` |
| Backend exposure | `docker compose port mcp-catalog-server 8080` → `127.0.0.1:8080` | loopback only |
| Frontend exposure | `docker compose port frontend 8080` → `127.0.0.1:4200` | loopback only |
| PostgreSQL exposure | `docker inspect` → `{"5432/tcp":null}` | unpublished |
| Origin/Host protection | GET `/mcp` 400; absent Origin 400; hostile Origin **403**; hostile Host **421** | unchanged |
| Actuator readiness | HTTP 200 | unchanged |
| Flyway history | V1 `162354501`, V2 `-2124124441`, both success | unchanged |
| Seed data | 24 rows | unchanged |

## 5. Files changed in this finishing step

Documentation only:

- `docs/SLICE_13_VALIDATION.md` — created (this record).
- `docs/MCP_Catalog_Platform_IMPLEMENTATION_PLAN_v0.2.md` — status and Slice 13 row moved
  from "in progress"/"In progress" to complete.
- `docs/TEST_PLAN.md` — Slice 12–13 smoke-coverage row now names the detail/history/offline
  checks.

`README.md`, `docs/ARCHITECTURE.md`, `frontend/README.md` and the Slice 13 code were
already updated correctly by the prior work and were left unchanged. None of the Slice 13
implementation was rewritten, and no production code was modified in this step.

## 6. Deviations from the plan

None. Slice 13 delivered the planned detail component/route, result-to-detail navigation,
API-client extension, readable not-found/request-failure states, focused tests and this
validation record; it used the accepted REST detail endpoint with no browser database
access, MCP call, replicated lookup policy or write capability. The extra tests for
inactive items, malformed IDs, retry and cancellation strengthen the plan's required cases
without broadening scope.

## 7. Remaining risks / notes

- The host has no Node 22.22.3 on `PATH`; the approved `.nvmrc` runtime is used from
  `/tmp/node-v22.22.3-linux-x64`. Running the frontend commands with the host default
  Node 24.18.0 would work but does not match the pinned `engines` under `engine-strict`.
- Return-to-search state is intentionally in-memory per tab session: a full reload or a
  directly opened detail link starts a new session and returns to the default search.
  This is documented behaviour, not a defect.
- `docs/SLICE_13_VALIDATION.md` was the only missing Slice 13 deliverable; everything else
  was already correct in the working tree.
- Nothing was committed, per instruction. Slice 13 remains uncommitted in the working tree.

## 8. Status

**Slice 13 complete.** Gates: Slice 13 detail tests 12/12; full frontend suite 40/40;
production build succeeded; backend `clean verify` 232/232; Compose healthy; browser smoke
passed for every required flow; backend, REST, MCP, migrations and security defaults
unchanged. **Slice 14 has not been started.**
