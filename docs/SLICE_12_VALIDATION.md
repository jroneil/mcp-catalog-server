# Slice 12 — Angular Catalog Search and Docker Integration

**Status:** Complete — 2026-09-24

## Objective and governing requirements

Implement [plan v0.2 Slice 12](MCP_Catalog_Platform_IMPLEMENTATION_PLAN_v0.2.md), PRD
FR-7 list/search/filter/results, FR-13A, technical/Docker/startup requirements §§10–12,
and frontend testing §16. D3 was approved in the conversation and recorded in v0.2 and
ARCHITECTURE before scaffolding. Slice 11's REST contract remains unchanged.

Delivered standalone Angular search/list UI, REST client, text/type/active/maximum-price
controls, optional page size, server-bounded pagination, readable results, distinct
loading/empty/validation/request/backend-unavailable states, frontend tests, production
build, development proxy and nginx/Compose integration.

## Toolchain decisions

| Component | Exact version / decision |
| --- | --- |
| Angular common/core/compiler/platform-browser/compiler-cli | 22.2.0 |
| Angular CLI/build | 22.1.8 (same major, compatible published peer ranges) |
| Node | 22.22.3; .nvmrc, package engines and node:22.22.3-bookworm-slim |
| npm | 10.9.8; bundled with approved Node; packageManager and engine pin |
| nginx | nginx:1.30.5-alpine3.24 |
| TypeScript | 6.0.3 |
| RxJS / tslib | 7.8.2 / 2.8.1 |
| Vitest / browser-playwright provider | 4.1.11 / 4.1.11 |
| jsdom / Playwright | 28.1.0 / 1.63.0 |
| Reproducibility | Exact direct dependencies plus package-lock.json; npm ci; no alternate package manager |

Stable Angular 22.2.0 and stable CLI/build 22.1.8 were verified through the normal npm
registry; no Angular next/RC release was selected. The approved Node satisfies their
published engines. The upstream build/test dependency graph includes prerelease-tagged
transitives (`gensync` 1.0.0-beta.2, `@polka/url` 1.0.0-next.29 and a nested
`@jridgewell/gen-mapping` 0.4.0-beta.0); these are inherited from the stable tooling,
locked without overrides, not chosen Angular prereleases. npm ci audited the installed
packages with zero reported vulnerabilities. No Java dependency changed.

## Files changed

Added under `frontend/`:

- Tooling: package.json, package-lock.json, angular.json, tsconfig.json,
  tsconfig.app.json, tsconfig.spec.json, .nvmrc, .npmrc, .editorconfig, .gitignore.
- Application: src/main.ts, src/index.html, src/styles.css,
  src/app/app.config.ts, app.ts, app.html, app.css, catalog-api.ts.
- Tests: src/app/catalog-api.spec.ts, src/app/app.spec.ts,
  scripts/catalog-smoke.mjs.
- Runtime/documentation: proxy.conf.json, Dockerfile, .dockerignore, nginx.conf, README.md.

Modified: root docker-compose.yml, .env.example, README.md, docs/ARCHITECTURE.md,
docs/TEST_PLAN.md, docs/MCP_Catalog_Platform_IMPLEMENTATION_PLAN_v0.2.md.
Added this validation record.

The partial CLI scaffold from the prior turn was inspected and reused. Its welcome
page and unused forms/router dependencies were removed. No generated example is claimed
as product functionality. All backend files and historical validation records remain
unchanged. README now correctly records 232 backend tests and the existing REST endpoints.

## Architecture and data flow

```text
Browser -> relative /api/v1/catalog
        -> nginx /api/ reverse proxy (Compose)
           OR Angular development proxy (host development)
        -> existing REST adapter -> CatalogService -> PostgreSQL
```

Angular owns presentation and interaction. Signals track loading, results and errors;
HttpClient performs searches and cancels superseded requests. Blank form controls are
omitted. Price strings retain their decimal scale, and no filtering/defaults/price
rounding/validation bounds are duplicated. Server metadata drives pagination. A new
search omits page to request the server default. Page navigation retains the submitted
filters even if the user edits the form without submitting. No unbounded fetch or
client-side filtering exists. No item detail navigation was added.

The browser displays safe REST 400 messages and generic text for transport/internal
failures. Failed requests clear stale results rather than presenting them as current
success. Retry reuses the failed request. Prices are displayed to two decimals without
inventing currency metadata absent from the accepted REST contract.

## Docker and routing

The build stage uses npm ci and npm run build; nginx serves only the built static assets.
SPA fallback resolves browser paths to index.html. /api/ forwards the original URI to
http://mcp-catalog-server:8080. /mcp and its subpaths return 404; no MCP proxy exists.

Frontend publishes `127.0.0.1:${FRONTEND_PORT:-4200}:8080` and waits for the healthy
backend. Its health check verifies static serving; backend readiness verifies database
connectivity. Existing backend publication and PostgreSQL service configuration are
unchanged. PostgreSQL remains unpublished. No CORS configuration, security bypass,
frontend secret or AI-provider environment variable was added.

Development uses npm start on 127.0.0.1:4200 and proxy.conf.json forwarding /api/** to
127.0.0.1:8080. The default backend host port is required by that approved dev proxy.
Compose and the dev server cannot occupy port 4200 simultaneously; README documents
stopping the frontend container while developing and restoring it afterward.

## Tests and build results

| Gate | Result |
| --- | --- |
| npm ci | Passed with Node 22.22.3/npm 10.9.8; lockfile reproducible; zero audit vulnerabilities |
| catalog-api.spec.ts | 9 passed: relative GET/defaults, every filter, combined/pagination requests, omitted versus invalid inputs |
| app.spec.ts | 16 passed: rendering, loading, empty, form mapping, defaults/bounds, page navigation, new search, 400/malformed/500/502/503/network states, retry, stale-request cancellation |
| Frontend tests total | 25 passed, 2 files; no skipped cases |
| Angular production build | Passed without warnings; initial bundle 159.79 kB, estimated transfer 47.58 kB |
| Full backend clean verify | BUILD SUCCESS: 232 tests, zero failures/errors/skips |
| Compose config/build/start | Passed; all three containers healthy |
| Production browser smoke | Passed in real installed Google Chrome using Playwright; no mocked API traffic or browser runtime errors |
| Development proxy smoke | Passed with the same browser script and PostgreSQL-backed backend |

The backend gate retains all accepted Phase 1/Slice 11 coverage, including mandatory
PostgreSQL Testcontainers, Flyway success/failure tests, REST/MCP equivalence and MCP
security/contract checks. No backend test was changed or waived.

## Exact validation commands

The host had no Node on PATH. The approved official Node 22.22.3 archive was extracted
under /tmp after verification against nodejs.org SHASUMS256.txt. Subsequent shell
commands used `export PATH=/tmp/node-v22.22.3-linux-x64/bin:$PATH`; no system-wide Node
upgrade was performed. Docker independently uses the pinned build image.

From frontend/ (final reproducible install and gates):

```bash
npm install --package-lock-only --no-fund
npm ci --no-fund > /tmp/slice12-ci.log 2>&1
npm test > /tmp/slice12-tests.log 2>&1
npm run build > /tmp/slice12-build.log 2>&1
```

From repository root:

```bash
mvn -f backend/pom.xml --batch-mode --no-transfer-progress clean verify > /tmp/slice12-backend.log 2>&1
docker compose config --quiet
docker compose up --build --wait --wait-timeout 300 > /tmp/slice12-compose.log 2>&1
CHROME_BIN=/usr/bin/google-chrome SMOKE_SCREENSHOT=/tmp/slice12-desktop.png SMOKE_MOBILE_SCREENSHOT=/tmp/slice12-mobile.png npm --prefix frontend run smoke
```

Development proxy check, with backend/PostgreSQL kept running:

```bash
docker compose stop frontend
npm --prefix frontend start > /tmp/slice12-dev.log 2>&1
# In another shell after startup:
ss -ltnp '( sport = :4200 )'
CHROME_BIN=/usr/bin/google-chrome npm --prefix frontend run smoke > /tmp/slice12-dev-smoke.log 2>&1
```

After terminating that dev server, all containers were recreated while retaining the
database volume (no volume deletion was required or performed):

```bash
docker compose down > /tmp/slice12-restart.log 2>&1
docker compose up --build --wait --wait-timeout 300 >> /tmp/slice12-restart.log 2>&1
# Run only after --wait has completed:
CHROME_BIN=/usr/bin/google-chrome SMOKE_SCREENSHOT=/tmp/slice12-desktop.png SMOKE_MOBILE_SCREENSHOT=/tmp/slice12-mobile.png npm --prefix frontend run smoke > /tmp/slice12-smoke-final.log 2>&1
docker compose ps
docker inspect --format '{{.Name}} health={{.State.Health.Status}} bindings={{json .HostConfig.PortBindings}}' mcp-catalog-server-frontend-1 mcp-catalog-server-mcp-catalog-server-1 mcp-catalog-server-postgres-1
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/readiness
curl --silent --output /dev/null --write-out '%{http_code}\n' http://127.0.0.1:4200/mcp
curl --silent --output /dev/null --write-out '%{http_code}\n' http://127.0.0.1:4200/catalog-search
curl --silent --output /dev/null --write-out '%{http_code}\n' -X POST http://127.0.0.1:8080/mcp -H 'Origin: http://evil.example' -H 'Accept: application/json, text/event-stream' -H 'Content-Type: application/json' -d 'not-json'
curl --silent --dump-header /tmp/slice12-response-headers.txt --output /dev/null -H 'Origin: http://example.invalid' http://127.0.0.1:4200/api/v1/catalog
if rg -i '^access-control-allow-origin:' /tmp/slice12-response-headers.txt; then exit 1; fi
docker compose logs --no-color mcp-catalog-server | rg 'Successfully validated|Schema .*up to date'
git diff --exit-code -- backend docs/SLICE_11_VALIDATION.md docs/MCP_Catalog_Platform_IMPLEMENTATION_PLAN_v0.1.md
git diff --check
```

Independent PostgreSQL evidence:

```bash
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT id, sku FROM public.catalog_item WHERE type = '\''SERVICE'\'' AND active = true AND price <= 200 ORDER BY id; SELECT version, success FROM public.flyway_schema_history ORDER BY installed_rank;"'
```

## Runtime and visual evidence

- All three Compose containers healthy after full recreation.
- Frontend: 127.0.0.1:4200 -> container 8080; backend: 127.0.0.1:8080 -> container 8080.
- PostgreSQL HostConfig.PortBindings is `{}`. nginx's inherited EXPOSE 80 metadata
  does not publish a host port.
- Backend health/readiness UP; Flyway validates unchanged V1/V2 and reports schema up to date.
- Frontend /mcp returns 404; SPA fallback returns 200; backend rejects hostile MCP Origin with 403.
- No Access-Control-Allow-Origin header on the same-origin frontend REST path.
- Initial UI: 20 of 24 records; next page displays 4 records, previous returns to page 1.
- SERVICE, active=true, maxPrice=200 returns six cards and API totalItems=6, IDs
  13,14,15,16,19,20; SKUs SVC-101, SVC-102, SVC-103, SVC-104, SVC-107, SVC-108.
- Independent SQL returned exactly those six IDs/SKUs. Both migration history entries succeeded.
- Empty criteria outcome and server pageSize=101 error are presented distinctly, without stale cards.
- API requests observed in Chrome remain on the frontend origin in both production and development.
- Desktop (1440px) and mobile (390px) captures were visually inspected. Cards/filters are readable,
  mobile has no horizontal overflow, and no browser runtime exceptions were observed.
  Local captures: /tmp/slice12-desktop.png and /tmp/slice12-mobile.png; not source artifacts.

## Corrections, deviations and limitations

The initial npm install crashed in optional-peer resolution; pinning the matching
Vitest browser provider fixed it without changing the approved versions or ignoring
peer checks. An initial stylesheet budget warning was removed by simplifying styles.
One final smoke attempt started before Compose finished its restart and failed with
connection refused; it was rerun successfully after --wait completed. No failure was waived.

No product-scope deviation. Upstream transitive prerelease labels are disclosed in the
toolchain section. Browser validation uses installed Chrome, not a full cross-browser
matrix. The frontend health probe checks static serving; the separate backend probe
checks JDBC readiness. The real-data smoke assumes the immutable seed dataset, while
unit tests use isolated fixtures. No externally hosted AI or manual database setup is
needed. Development and container port 4200 are mutually exclusive on one host.

**No Slice 13 or later scope was added.** No detail page, AI UI, Ollama/hosted provider,
natural-language workflow, writes/admin, authentication or Phase 3 functionality.
CatalogService, persistence, migrations, REST contract, MCP behavior and backend
security defaults remain unchanged. Nothing was committed.
