# Slice 10 validation — Phase 1 acceptance audit

Executed 2026-09-23. **Phase 1 PASSES.** Phase 2 has not been started.

## 1. Executive result

**Phase 1: PASS.**

Every Phase 1 functional requirement, security/network requirement and acceptance
criterion in PRD v0.3 and the implementation plan v0.1 was independently verified against
the code, configuration, tests and the running system. `mvn clean verify` passes with
**184 tests, 0 failures**; a clean database bootstrap and `docker compose up --build
--wait` from an empty volume succeed; exactly the two intended MCP tools are registered
and work against PostgreSQL; documented security defaults hold; no Phase 2/3 scope creep
was found. Two documentation defects were found and fixed (stale slice-status statements;
a missing `docs/TEST_PLAN.md` required by PRD §19). **No production code was changed.**

## 2. Scope audited

The whole Phase 1 deliverable at commit `5be3ccf` plus the working-tree documentation
changes:

- `backend/` — Java 21 / Spring Boot 4.1.1 backend, 21 production classes, 17 test
  classes, `pom.xml`, `application.yml`, two Flyway migrations, `Dockerfile`
- `docker-compose.yml`, `.env.example`, `.gitignore`, `README.md`, `docs/`
- Runtime: Docker Compose stack (backend + PostgreSQL 18.6), MCP endpoint `/mcp`

Out of scope by definition: Phase 2 (Angular, REST, AI providers) and Phase 3 (MCP
resources/prompts/STDIO, auth, observability stack, Python, Java MCP client).

## 3. Source documents reviewed

| Document | Role |
| --- | --- |
| `docs/MCP_Catalog_Platform_PRD_v0.3.md` | Requirements, acceptance criteria, exclusions, DoD |
| `docs/MCP_Catalog_Platform_IMPLEMENTATION_PLAN_v0.1.md` | Slice definitions, final acceptance checklist, stop conditions |
| `docs/ARCHITECTURE.md` | Pinned decisions, compatibility evidence, boundaries, security model |
| `docs/SLICE_1_VALIDATION.md` … `docs/SLICE_9_VALIDATION.md` | Per-slice evidence records |
| `docs/Model_Context_Protocol_Enhanced_Java_Spring_Reference.md` | Input reference (non-governing) |
| `README.md`, `backend/pom.xml`, `backend/src/**`, `docker-compose.yml`, `.env.example` | Implementation under audit |

No `AGENTS.md` or equivalent repository instruction file exists (`ls` confirmed); the PRD,
the implementation plan and `docs/ARCHITECTURE.md` are the governing documents.

**Audit method:** every claim below was re-derived from code, configuration, live
protocol traffic or a re-run gate — not from the prior slices' summaries. Prior slice
documents were used only as a cross-check for drift.

## 4. Production code changes

**None.** No production source, configuration, dependency, migration or Docker file was
modified. The audit found no Phase 1 defect requiring a code change.

Changes made in this slice (documentation only):

| File | Change | Reason |
| --- | --- | --- |
| `docs/TEST_PLAN.md` | **created** | PRD §19 and the plan's Slice 10 deliverables require `docs/TEST_PLAN.md`; it was missing. Content documents the already-implemented suite only. |
| `docs/ARCHITECTURE.md` | 5 stale statements corrected | Slice-status drift: "Slice 8 remains unstarted" (in the Slice 7 section), "Later host validation needs a user-configured Claude Code installation", "Slices 1–9 are implemented; Slice 10 has not been started", the scope line, and a present-tense "Slice 4 adds … no tool" |
| `README.md` | 3 stale statements corrected | "Slices 1–8", "Slice 8 has not been started", section header "MCP server and tools (Slices 4–7)" |

Historical `SLICE_1…SLICE_9_VALIDATION.md` files were deliberately **not** edited: they
are point-in-time records (precedent established in earlier slices).

## 5. Requirement / acceptance matrix

Statuses: **PASS** = verified with concrete evidence; **N/A** = out of Phase 1 scope.

### 5.1 PRD functional requirements (Phase 1)

| Requirement | Source | Evidence | Status |
| --- | --- | --- | --- |
| `search_catalog` with optional type, active, maxPrice, text, page, pageSize | PRD FR-1 | `SearchCatalogTool`; live schema `params=[active,maxPrice,page,pageSize,text,type]`; `SearchCatalogToolTest` (13) | PASS |
| Phase 1 text search is simple case-insensitive SQL, not full-text | PRD FR-1 | `CatalogItemSearchImpl` uses `ILIKE … ESCAPE '!'`; no `tsvector`/`tsquery`; `CatalogServicePostgresTest` (23) | PASS |
| Pagination defaults page 0 / pageSize 20 / maximum 100 | PRD FR-1 | `CatalogService.DEFAULT_PAGE/DEFAULT_PAGE_SIZE/MAX_PAGE_SIZE`; live no-filter call → `page=0 size=20`; `pageSize=100` accepted; 101 rejected | PASS |
| Invalid pagination rejected; no unbounded result set | PRD FR-1 | Live: `pageSize=101` → `Page size must be between 1 and 100`; `page=-1` → `Page must be between 0 and 10000`; every data query carries `LIMIT :limit OFFSET :offset` | PASS |
| Input bounds for text length, price, page, pageSize, enum | PRD FR-1 | `CatalogService` constants and validation; live tool errors for all five; `CatalogServiceTest` (25) | PASS |
| Tool returns only records matching the filters | PRD FR-1 | Live: PRODUCT 12, SERVICE 12, active true 18, active false 6, `maxPrice=199` 15, combined SERVICE+active+≤200 → exactly ids `13,14,15,16,19,20` | PASS |
| `get_catalog_item` by identifier; not found returns a clear error, no fabrication | PRD FR-2 | `GetCatalogItemTool` + `CatalogService.getItem`; live `id=99999` → `isError=true "Catalog item not found: 99999"`; `GetCatalogItemMcpIntegrationTest` (12) | PASS |
| Tool metadata: descriptive name, clear description, explicit parameter descriptions, machine-readable schema, predictable structured output | PRD FR-3 | Live `tools/list` shows both tools with descriptions and per-parameter descriptions; `$schema` draft 2020-12; `additionalProperties:false`; asserted by `SearchCatalogToolTest`/`GetCatalogItemToolTest` | PASS |
| Tool contracts treated as versioned APIs | PRD FR-3A | `PhaseOneEndToEndMcpTest` freezes both input schemas by exact equality with stored documents; ARCHITECTURE/README document both contracts | PASS |
| Streamable HTTP transport | PRD FR-4 | `application.yml` `protocol=STREAMABLE`, `type=SYNC`, endpoint `/mcp`; live `initialize` → protocol 2025-11-25; no SSE or STDIO beans (`PhaseOneEndToEndMcpTest`) | PASS |
| Host-exposed ports bind to loopback by default | PRD FR-4 | `server.address=${SERVER_ADDRESS:127.0.0.1}`; Compose publishes `127.0.0.1:${BACKEND_PORT:-8080}:8080`; `docker compose port` → `127.0.0.1:8080` | PASS |
| Docker does not accidentally publish on all interfaces | PRD FR-4 | `docker inspect` → only `8080/tcp` → HostIp `127.0.0.1`; container-internal `SERVER_ADDRESS=0.0.0.0` is documented and does not widen the host mapping | PASS |
| Origin validation (or equivalent) for local HTTP MCP | PRD FR-4 | `DefaultServerTransportSecurityValidator` wired in `McpServerConfiguration`; live: hostile Origin → **403**, hostile Host → **421**, absent/loopback Origin accepted; `McpTransportSecurityTest` (9) | PASS |
| Broadening network exposure is explicit and documented | PRD FR-4 | No broadening present; overrides are environment-driven and documented in ARCHITECTURE/README | PASS |
| Testable with MCP Inspector; docs cover startup, connect, discovery, invocation, failure | PRD FR-5 | `SLICE_8_VALIDATION.md` (Inspector 2.7.0, web UI + CLI, all 11 checks); README "Connecting MCP Inspector" section | PASS |
| At least one real MCP host connects and invokes a catalog tool | PRD FR-6 | `SLICE_9_VALIDATION.md`: opencode 1.18.23 + local Ollama; 4/4 natural-language scenarios, grounded answers, server logged the `initialize` handshake | PASS |
| Angular frontend | PRD FR-7 | Phase 2 | N/A |
| REST under `/api/v1/catalog` | PRD FR-8 | Phase 2; no controller exists (bytecode scan asserts no `@RestController`/`@RequestMapping`) | N/A |
| Shared business logic REST+MCP | PRD FR-9 | Phase 2 (MCP already shares `CatalogService`) | N/A |
| Ollama / hosted provider in the application | PRD FR-10, FR-11, FR-12 | Phase 2; no provider dependency or configuration in `backend/` | N/A |
| AI demonstration workflow | PRD FR-13 | Phase 2 | N/A |
| Browser origin policy / CORS allowlist | PRD FR-13A | Phase 2 (no browser client) | N/A |
| MCP resource / prompt / STDIO / Java client / Python / auth / observability stack | PRD FR-14 … FR-20 | Phase 3 | N/A |

### 5.2 PRD non-functional, security and documentation requirements

| Requirement | Source | Evidence | Status |
| --- | --- | --- | --- |
| Java 21 | PRD §10 | `java.version=21`; compiler log `[debug parameters release 21]` | PASS |
| Spring Boot / Spring AI / MCP integration | PRD §10 | Boot parent `4.1.1`; `spring-ai-starter-mcp-server-webmvc` 2.0.1; resolved `mcp-core` 2.0.0 | PASS |
| Spring Data | PRD §10 | `spring-boot-starter-data-jdbc` (Spring Data JDBC 4.1.1) | PASS |
| PostgreSQL as the persistence path | PRD §10 | PostgreSQL 18.6 containers; JDBC 42.7.13; no H2 in the dependency tree | PASS |
| Flyway owns schema and migrations are immutable | PRD §10 | Only `V1__create_catalog_item.sql`, `V2__seed_catalog_items.sql`; `flyway_schema_history` checksums `162354501` / `-2124124441`, both success | PASS |
| Explicit version pinning for critical dependencies | PRD §10 | `pom.xml` properties + imported BOMs; resolved tree matches pins | PASS |
| MCP server/application version begins at 0.1.0 | PRD §10 | Live `serverInfo {name: mcp-catalog-server, version: 0.1.0}` | PASS |
| MCP endpoint `/mcp`, Streamable HTTP | PRD §10, §12 | `streamable-http.mcp-endpoint=/mcp`; live endpoint answers at `http://127.0.0.1:8080/mcp` | PASS |
| Dockerized from the beginning | PRD §11 | `backend/Dockerfile` (multi-stage, non-root, healthcheck), `docker-compose.yml` with backend + postgres | PASS |
| Phase 1 Compose services: server + postgres | PRD §11 | `docker compose config`/`ps` → exactly those two services | PASS |
| Host port mappings loopback by default | PRD §11 | `127.0.0.1:${BACKEND_PORT:-8080}:8080`; PostgreSQL has no `ports:` | PASS |
| No hard-coded secrets; `.env` local only; `.env.example` placeholders only | PRD §11 | Only `.env.example` is tracked (all placeholders); `.env` is gitignored; no credential literal in tracked files | PASS |
| Health checks where practical; not assuming PostgreSQL is up | PRD §11 | `pg_isready` healthcheck + `depends_on: service_healthy`; Actuator readiness includes `db`; backend container healthcheck | PASS |
| MCP is an adapter, no business logic in it | PRD AC-1 | `McpArchitectureBoundaryTest`: no `catalog/persistence` or `java/sql` reference from `mcp/**`; adapters depend on `CatalogService` only | PASS |
| REST is an adapter (no controllers in Phase 1) | PRD AC-2 | No REST adapter exists | N/A |
| Both adapters use the same service layer | PRD AC-3 | Only the MCP adapter exists in Phase 1 and it calls `CatalogService`; architecture check confirms | PASS |
| Repositories encapsulate persistence | PRD AC-4 | SQL confined to `CatalogItemSearchImpl`; architecture check confirms no SQL outside persistence | PASS |
| Transport-specific objects do not leak into service APIs | PRD AC-5 | `catalog/application` has no MCP/web/servlet references (bytecode check); MCP-facing records live in `mcp/tools` | PASS |
| Tool inputs/outputs explicit and intentional | PRD AC-6 | Generated schemas + documented JSON result shape; contract freeze test | PASS |
| Prefer maintainability over excessive abstraction | PRD AC-7 | Thin adapters, one decorator per concern, no speculative layers | PASS |
| AI/MCP arguments treated as untrusted input | PRD AC-8 | Arguments validated by `CatalogService` (bounds) and by SDK schema validation (types/required); bound SQL parameters only | PASS |
| Errors useful, no stack traces or secrets, never fabricate success | PRD §14 | Live error texts are the service messages; `SanitizingToolCallback` replaces unexpected failures with a fixed safe message; `SanitizingToolCallbackTest` (10) + `McpInternalFailureSanitizationTest` (7) | PASS |
| Phase 1 security: no committed secrets, externalized config, no sensitive logging, loopback default, untrusted-argument validation, input bounds, Origin protection, documented non-hardened | PRD §15 | As above; `.env` externalized; sanitizer logs the failure server-side without tool arguments; allowlists loopback-only; README/ARCHITECTURE state Phase 1 is not production-hardened | PASS |
| Testing strategy: unit, repository integration, MCP tool, MCP integration | PRD §16 | 184 tests across 17 suites; see `docs/TEST_PLAN.md` §4 mapping | PASS |
| Manual compatibility: Inspector + one real host | PRD §16 | `SLICE_8_VALIDATION.md`, `SLICE_9_VALIDATION.md` | PASS |
| Observability: health endpoint, meaningful startup logs, migration visibility, MCP diagnostics | PRD §17 | `/actuator/health` + readiness; Flyway validation/migration logs; `Registered tools: 2`; MCP `Client initialize request` logs | PASS |
| No prompt/business data logged by default | PRD §17 | No request-body logging configured; sanitizer logs tool name + throwable only | PASS |
| Documentation requirements (README, PRD, ARCHITECTURE, IMPLEMENTATION_PLAN, TEST_PLAN) | PRD §19 | All present. **Gap found and fixed:** `docs/TEST_PLAN.md` was missing and has been created. PRD/plan exist under versioned descriptive filenames (accepted deviation, §16 below) | PASS (after fix) |
| Documentation includes prerequisites, Docker startup, Inspector setup, architecture, tool inventory, testing commands, known limitations | PRD §19 | README + ARCHITECTURE + TEST_PLAN + validation records | PASS |
| Feature DoD: implemented, tests pass, error cases covered, no secrets, Docker valid, docs updated, boundaries intact | PRD §22 | Verified throughout this audit | PASS |

### 5.3 PRD acceptance criteria (§12 startup, §21 exit, §22 project DoD)

| Acceptance criterion | Source | Evidence | Status |
| --- | --- | --- | --- |
| 1. PostgreSQL starts and becomes healthy | PRD §12 | `docker compose up --build --wait` from a removed volume → `postgres … healthy` | PASS |
| 2. Spring Boot starts | PRD §12 | Log `Started McpCatalogApplication`; backend container healthy | PASS |
| 3. Flyway migrations execute | PRD §12 | Fresh volume: `Migrating schema "public" to version "1 …"` then `"2 …"`, `Successfully applied 2 migrations … now at version v2` | PASS |
| 4. Catalog schema exists | PRD §12 | `information_schema.tables` in `public` → exactly `catalog_item`, `flyway_schema_history` | PASS |
| 5. Seed data available | PRD §12 | 24 rows, 24 distinct SKUs, prices 0.00–1249.00, PRODUCT 3/9 and SERVICE 3/9 (inactive/active) | PASS |
| 6. Application health reports ready | PRD §12 | `/actuator/health` and `/actuator/health/readiness` both HTTP 200 `UP` | PASS |
| 7. MCP endpoint available | PRD §12 | Live `initialize`/`tools/list`/`tools/call` over `http://127.0.0.1:8080/mcp` | PASS |
| 8. MCP Inspector can discover configured tools | PRD §12 | `SLICE_8_VALIDATION.md`: Inspector UI + CLI discovered exactly two tools | PASS |
| Clean Docker startup succeeds | PRD §21 | Verified from `docker compose down -v` in this audit | PASS |
| Both tools discoverable | PRD §21 | Live `tools/list` → `[get_catalog_item, search_catalog]` | PASS |
| Both tools operate against PostgreSQL | PRD §21 | Returned rows match the seeded table exactly (ids/SKUs/prices/timestamps) | PASS |
| Automated tests pass | PRD §21 | 184/184 | PASS |
| Inspector can invoke both tools | PRD §21 | `SLICE_8_VALIDATION.md` scenarios 7–10 | PASS |
| One real local MCP host can invoke a tool | PRD §21 | `SLICE_9_VALIDATION.md` scenarios A–D | PASS |
| Host-exposed ports bind to loopback by default | PRD §21 | `docker compose port` → `127.0.0.1:8080` | PASS |
| Input bounds and pagination limits enforced | PRD §21 | Live rejections for pageSize 101, page −1, maxPrice −1, invalid type, 201-char text; no unbounded query | PASS |
| MCP tool contracts documented | PRD §21 | README tool inventory, ARCHITECTURE contract sections, SLICE_5/6/7 records, contract-freeze test | PASS |
| Failure cases return useful errors | PRD §21 | Not-found, invalid identifier, invalid criteria all return precise `isError` text | PASS |
| Project DoD: clone → `docker compose up --build` → Inspector → discover tools → query real data → correct structured results without manual DB setup | PRD §22 | Reproduced in this audit end to end; `.env` is the only local prerequisite (documented) | PASS |

### 5.4 Implementation plan — Final Acceptance Checklist

| Checklist item | Evidence | Status |
| --- | --- | --- |
| Java 21 backend builds | `clean verify` BUILD SUCCESS | PASS |
| Critical dependency versions pinned | `pom.xml` + resolved tree | PASS |
| PostgreSQL is the real persistence path | Live queries; no H2 | PASS |
| Flyway owns schema creation | Fresh-volume bootstrap; only 2 tables | PASS |
| Realistic seed data loads | 24 business records | PASS |
| Docker Compose starts cleanly from a fresh checkout | `down -v` → `up --build --wait` | PASS |
| Health endpoint reports ready | health + readiness 200 | PASS |
| Host-exposed HTTP ports bind to `127.0.0.1` | `docker compose port`, `docker inspect` | PASS |
| Origin protection implemented/documented | Validator wired; tests + live 403 | PASS |
| `search_catalog` discoverable | Live `tools/list` | PASS |
| `get_catalog_item` discoverable | Live `tools/list` | PASS |
| Both tools operate against PostgreSQL | Live calls match seeded rows | PASS |
| Search pagination bounded | `LIMIT` always present; max pageSize 100 | PASS |
| Input validation enforced | Service validation + SDK schema validation | PASS |
| AI/MCP arguments treated as untrusted input | Bounds + bound SQL parameters | PASS |
| Tool contracts documented as versioned APIs | Contract freeze test + docs | PASS |
| Unit tests pass | 184/184 (incl. 38 unit cases) | PASS |
| PostgreSQL repository integration tests pass | Testcontainers suites | PASS |
| MCP tool tests pass | Tool unit + integration suites | PASS |
| MCP end-to-end integration tests pass | `PhaseOneEndToEndMcpTest` (23) | PASS |
| MCP Inspector can invoke both tools | Slice 8 record | PASS |
| One real local MCP host can invoke a catalog tool | Slice 9 record | PASS |
| Errors useful, no stack traces/secrets | Error paths + sanitizer tests | PASS |
| No real secrets committed | Only `.env.example` tracked | PASS |
| Angular has not been added | No frontend files | PASS |
| Ollama/hosted LLM integration has not been added to the app | No provider dependency/config in `backend/` | PASS |
| Python examples have not been added | No `*.py`, no `examples/` | PASS |
| MCP resources/prompts/STDIO have not been added | Capabilities tool-only; `stdio: false`; no resource/prompt beans | PASS |
| Documentation matches implemented behaviour | Drift fixed in this slice | PASS |

### 5.5 Phase 1 exclusions verified (PRD §21 exclusions)

| Exclusion | Verified absent |
| --- | --- |
| Angular | No frontend directory, no `package.json`/`angular.json`/`*.ts` |
| Ollama integration (application) | No `ollama`/`openai`/`anthropic`/`bedrock`/`vertex`/`langchain` reference in `backend/` |
| Hosted LLM integration | Same; `opencode auth list` → 0 credentials used during Slice 9 |
| Python examples | No `.py` files, no `examples/` |
| MCP resources | `capabilities.resource=false`; no resource specification registered |
| MCP prompts | `capabilities.prompt=false`; no prompt specification registered |
| STDIO | `stdio: false`; no STDIO transport bean |
| Production authentication | No security starter, no auth configuration |
| Production observability stack | Actuator health only; no metrics/tracing stack |

## 6. Architecture verification (from code and configuration)

| Aspect | Verified value | Source |
| --- | --- | --- |
| Language/build | Java 21, Maven, `release 21`, `-parameters` enabled | `pom.xml`, compiler output |
| Framework baseline | Spring Boot `4.1.1` (parent, starters, plugins, BOM) | `pom.xml` |
| Spring AI / MCP | `spring-ai-starter-mcp-server-webmvc` 2.0.1 → `mcp-core` 2.0.0 | resolved dependency tree |
| Persistence | Spring Data JDBC + `NamedParameterJdbcTemplate` for the search fragment | `CatalogItemSearchImpl` |
| Migrations | Flyway 12.4.0, `classpath:db/migration`, `public`, validate-on-migrate, clean disabled | `application.yml` |
| Seed data | `V2` inserts 24 deterministic rows (fixed IDs/timestamps) | `V2__seed_catalog_items.sql` |
| Layers | `catalog.application` (service/DTOs) → `catalog.persistence` (entity/repository/SQL); `mcp.config` (wiring) + `mcp.tools` (adapters) | source layout |
| MCP wiring | `McpServerConfiguration` supplies the Origin-validating `WebMvcStreamableServerTransportProvider`; `McpToolConfiguration` registers one `ToolCallbackProvider` | code |
| Transport | Streamable HTTP, `type=SYNC`, endpoint `/mcp`, no SSE/STATIO/stateless | `application.yml`, live initialize |
| Tools | Exactly 2 registered (`Registered tools: 2`; live `tools/list`) | live |
| Capabilities | `logging` + `tools` only | live initialize |
| Error handling | Service exceptions preserved; unexpected failures sanitized at the MCP boundary | code + tests |
| Exposure defaults | Backend `127.0.0.1:8080` only; PostgreSQL unpublished | `docker compose port`, `docker inspect` |

## 7. MCP tool inventory and schemas

Live `tools/list` (authoritative), matching the documented contracts:

```text
count: 2
get_catalog_item: required=['id'] params=['id']
search_catalog:   required=[]     params=['active','maxPrice','page','pageSize','text','type']
```

- `search_catalog`: `type` string, `active` boolean, `maxPrice` number, `text` string,
  `page` integer, `pageSize` integer; all optional; `additionalProperties:false`;
  draft 2020-12.
- `get_catalog_item`: `id` integer, required.
- Result shapes: search returns `{items[],page,pageSize,totalItems,totalPages}` as a JSON
  text block; detail returns the item object. Timestamps are ISO-8601 UTC; prices keep two
  decimals on the wire (`199.00`).
- Documentation cross-check: README's parameter tables, ARCHITECTURE's contract sections
  and the Slice 5/6/7 records agree with the live schemas. No contract drift.

## 8. Search behaviour verification

Verified live against the freshly bootstrapped database and by the service tests:

| Behaviour | Observed |
| --- | --- |
| No filter | total 24, `page=0`, `pageSize=20`, 20 returned, ids ascending from 1 |
| `type=PRODUCT` / `type=SERVICE` | 12 / 12, each restricted to its type |
| `active=true` / `active=false` | 18 / 6 |
| `maxPrice=199.00` (inclusive) | 15, includes the 199.00 rows |
| `text=network` vs `NETWORK` | identical results (4 ids `2,6,16,19`) → case-insensitive |
| Searchable fields | SKU, name **and** description (id 8 matches "keyboard" only via its description) |
| Combined filters | `SERVICE + active + maxPrice 200` → exactly `13,14,15,16,19,20` |
| Defaults | `page=0`, `pageSize=20` |
| Maximum page size | `pageSize=100` → 24 returned, `pageSize=100` echoed |
| Out-of-range page | `page=5` → 0 items, totals preserved (`totalItems=24`, `totalPages=2`) |
| No unbounded path | every data query has `LIMIT`; `pageSize=101` → error |
| Deterministic ordering | `ORDER BY id ASC` (unique primary key); repeated calls identical |
| Simple SQL, no full-text upgrade | `ILIKE … ESCAPE '!'` on three columns; no `to_tsvector`/`ranking` |
| Injection safety | all caller values are bound parameters; `%`, `_`, `!` escaped |

## 9. Persistence / Flyway verification

- **Clean bootstrap:** volume removed (`docker compose down -v`) then
  `docker compose up --build --wait` from empty state — succeeded, both services healthy.
- **Migrations applied:** V1 then V2, `Successfully applied 2 migrations … now at version v2`.
- **History:** `1 | create catalog item | 162354501 | t`, `2 | seed catalog items | -2124124441 | t` — identical checksums to earlier slices, so migrations are unchanged and deterministic.
- **Schema inventory:** exactly `catalog_item` and `flyway_schema_history` → no ad-hoc DDL
  or alternate initialisation path.
- **Seed determinism:** 24 rows, 24 distinct SKUs, prices 0.00–1249.00, PRODUCT 3/9 and
  SERVICE 3/9; fixture rows `1/16/22` match the documented values.
- **No migrations rewritten**; no `baseline-on-migrate`, `clean`, or `repair` enabled.

## 10. Security / network verification

| Check | Observed |
| --- | --- |
| Backend publication | `127.0.0.1:8080` only (`docker compose port`; `docker inspect` → single HostIp `127.0.0.1`) |
| PostgreSQL publication | `5432/tcp` = `null` — unpublished |
| Hostile `Origin` | HTTP **403** `Invalid Origin header` |
| Hostile `Host` | HTTP **421** `Invalid Host header` |
| Legitimate loopback `Origin` (`http://127.0.0.1:8080`, `http://localhost:6274`) | accepted (request reaches JSON-RPC handling) |
| Non-browser client (no `Origin`) | accepted — MCP clients connect (Inspector CLI, opencode) |
| `/mcp` route present | GET → 400 (Accept header required), not 404 |
| Permanent broadening from Slice 8/9 | none — `application.yml`, Compose and allowlists are unmodified; Slice 8's temporary override lived in `/tmp` and was removed |
| Committed secrets | none — only `.env.example` (placeholders) is tracked; `.env` gitignored |
| Credentials required by Phase 1 runtime | only the local PostgreSQL credentials from `.env` |

## 11. Maven / test results

```text
mvn -f backend/pom.xml --batch-mode --no-transfer-progress clean verify
BUILD SUCCESS — Tests run: 184, Failures: 0, Errors: 0, Skipped: 0
```

17 suites; per-suite counts recorded in `docs/TEST_PLAN.md` §3. The expected total is
**confirmed as 184**, not assumed. Testcontainers-backed PostgreSQL suites ran (Docker
available); nothing was skipped. The full breakdown and gate were re-run in this audit.

## 12. Docker / health / readiness results

| Check | Result |
| --- | --- |
| `docker compose config --quiet` | OK |
| `docker compose down -v` (fresh volume) | OK, volume removed |
| `docker compose up --build --wait --wait-timeout 300` | OK from empty state |
| Services | backend `Up … (healthy)`, postgres `Up … (healthy)` |
| Aggregate health | HTTP 200 `{"groups":["liveness","readiness"],"status":"UP"}` |
| Readiness | HTTP 200 `{"status":"UP"}` |
| Tool registration | log `Registered tools: 2` |
| Startup | log `Started McpCatalogApplication in ~3.8s` |

## 13. End-to-end MCP smoke results (independent re-run)

Performed with an already-installed MCP client (MCP Inspector 2.7.0 CLI) against the
freshly bootstrapped stack, plus raw protocol inspection for diagnostics. No new framework
was introduced, and no application data was fabricated — every value came from PostgreSQL.

| Check | Result |
| --- | --- |
| 1. initialize/connect | `serverInfo {name: mcp-catalog-server, version: 0.1.0}`, capabilities `[logging, tools]` |
| 2. Discovery returns exactly the intended tools | `[get_catalog_item, search_catalog]` — count 2 |
| 3. `search_catalog` against real PostgreSQL | no-filter total 24; PRODUCT 12; SERVICE 12; active true 18 / false 6; `maxPrice` 15; case-insensitive text 4; combined → `13,14,15,16,19,20`; `pageSize=100` → 24; `page=5` → empty with totals |
| 4. `get_catalog_item` against real PostgreSQL | `id=16` → `SVC-104 / Network Health Assessment / 199.00 / active true`; `id=22` → inactive row retrievable |
| 5. Not-found behaviour | `id=99999` → `isError=true "Catalog item not found: 99999"`, no fabricated item |
| 6. Invalid input rejected | `pageSize=101`, `page=-1`, `maxPrice=-1`, `type=FOO`, 201-char text → precise tool errors; `id=0/-5` → `Catalog item ID must be positive`; missing/`"abc"` `id` → schema validation error |
| 7. Real data only | All returned rows match the seeded table; the catalog is fictional, so no answer is derivable without the tool |

**Tooling observation (not a server defect):** the Inspector CLI's `--tool-args-json`
mangles JSON **string** values — `{"id":"abc"}` was transmitted without the `id` property
and produced `required property 'id' not found`. A raw JSON-RPC `tools/call` with the same
payload correctly returned `[/id: string found, integer expected]`, and the automated SDK
test `GetCatalogItemMcpIntegrationTest.nonIntegerIdentifierIsRejectedCleanly` covers the
same case. The server contract is correct; the CLI argument handling is the anomaly.

## 14. Documentation consistency findings

| Finding | Severity | Action |
| --- | --- | --- |
| `docs/TEST_PLAN.md` missing although PRD §19 and the plan's Slice 10 deliverables require it | **Gap** | Created — documents the implemented suite (184 tests, 17 suites), commands, PRD §16 mapping, error-case coverage and known limitations. No new tests claimed. |
| `docs/ARCHITECTURE.md` said "Slice 8 remains unstarted" inside the Slice 7 section | Drift | Corrected |
| `docs/ARCHITECTURE.md` said "Later host validation needs a user-configured Claude Code installation/account" | Drift | Corrected — real-host validation completed in Slice 9 via opencode + Ollama; Claude Code additionally needs credentials |
| `docs/ARCHITECTURE.md` said "Slices 1–9 are implemented; Slice 10 has not been started" and scope "Slices 1–9" | Drift | Updated to Slices 1–10 / Phase 1 complete / Phase 2 not started |
| `docs/ARCHITECTURE.md` Slice 4 section used present tense "Slice 4 adds … no tool" | Ambiguity | Changed to "added" |
| `README.md` said "Slices 1–8", "Slice 8 has not been started", header "(Slices 4–7)" | Drift | Corrected to Slices 1–10 / Phase 1 complete, Slice 10 complete, header "(Slices 4–9)" |
| Historical `SLICE_1…9_VALIDATION.md` contain their own point-in-time "next slice not started" statements | Not drift | Left unchanged by design (historical records) |
| README test count | Accurate | 184 confirmed |
| Endpoint, transport, tool names, schemas, pagination, search semantics, security defaults, Docker behaviour, DB exposure in README/ARCHITECTURE | Consistent with implementation | No change needed |
| Repository state: commit `5be3ccf` contains only `docs/SLICE_9_VALIDATION.md`; Slice 9's README/ARCHITECTURE updates were uncommitted in the working tree | Process observation | Documented; nothing committed in this slice (per instruction) |

## 15. Phase 1 exclusions verified

All nine Phase 1 exclusions were confirmed absent by filesystem, dependency and runtime
checks (section 5.5): Angular, application-side Ollama, hosted LLM, Python, MCP resources,
MCP prompts, STDIO, production authentication, production observability stack. No Phase 2/3
functionality is required for any Phase 1 criterion to pass.

## 16. Deviations from PRD/plan

| Deviation | Assessment |
| --- | --- |
| `backend/` instead of the PRD's illustrative `server-java/` | Accepted and documented in ARCHITECTURE; the implementation plan and task specify `backend/` |
| PRD/plan documents use versioned descriptive filenames (`MCP_Catalog_Platform_PRD_v0.3.md`, `MCP_Catalog_Platform_IMPLEMENTATION_PLAN_v0.1.md`) rather than `PRD.md`/`IMPLEMENTATION_PLAN.md` | Accepted; content present, links consistent, ARCHITECTURE documents the layout. Renaming would churn links for no requirement benefit |
| Slice 9 host is opencode rather than the decision-gate's Claude Code | Claude Code connects but is unauthenticated in this environment; the PRD explicitly does not fix the host, and Slice 9 used the permitted already-installed alternative |
| Host `SERVER_ADDRESS=0.0.0.0` inside the container | Intentional and documented: required for Docker port forwarding; host publication remains hardcoded to `127.0.0.1` |
| `request`-time tool-set narrowing in the Slice 9 host configuration | Host-side validation configuration only; the server and both tool contracts are unaffected |

No deviation affects a Phase 1 acceptance criterion.

## 17. Remaining known risks

Distinguishing accepted limitations from Phase 1 failures — **none of the following is a
Phase 1 failure**:

**Accepted technical limitations**

1. MCP tool results are delivered as a JSON text block; Spring AI 2.0.1's `ToolCallback`
   conversion does not expose `outputSchema`/`structuredContent`. Documented in
   ARCHITECTURE and Slice 5/7.
2. SDK-owned schema-validation wording for malformed arguments (for example
   `[/id: string found, integer expected]`) can change on an SDK upgrade; it is
   non-sensitive today.
3. The transport provider bean overrides a Spring AI auto-configuration, so a Spring AI
   upgrade requires re-checking the builder arguments and back-off condition.
4. Docker image tags are pinned by release tag, not digest; upstream OS rebuilds can
   change image contents.
5. The local Compose database uses a single shared migration/runtime role; separate roles
   are future deployment hardening.
6. Offset pagination can become expensive near the configured page cap, and future
   concurrent writers could shift pages (no write path exists in Phase 1).
7. Inspector CLI `--tool-args-json` mis-handles JSON string values (tooling bug, verified
   against the raw protocol).
8. Phase 1 is explicitly **not** production-hardened: no authentication, no TLS, no rate
   limiting, no log redaction of unexpected exception text server-side.

**Environment/process risks**

9. Claude Code on this host remains unauthenticated, so its natural-language path is
   unvalidated; only its MCP connection is proven.
10. The fast 14B local model cannot drive Ollama tool calls here, so a recurring
    local-agent validation depends on the slower 79.7B model.
11. Slice 9's README/ARCHITECTURE documentation updates are uncommitted in the working
    tree (the slice's validation document is committed).

**Not failures:** no unresolved Phase 1 defect, no failing gate, no unmet acceptance
criterion.

## 18. Final Phase 1 disposition

**Phase 1 is complete and accepted.** All PRD v0.3 Phase 1 functional requirements,
security/network requirements, startup acceptance criteria, Phase 1 exit criteria and
project-level Definition of Done are satisfied, with the implementation-plan final
acceptance checklist fully green. Automated verification passes (184/184), clean Docker
startup from an empty volume passes, Flyway-managed schema and seed data are healthy and
deterministic, exactly the two intended MCP tools are discoverable and operate against
PostgreSQL, error paths are useful and non-leaking, security defaults hold, and no
Phase 2/3 scope creep exists. The two documentation defects found during the audit were
fixed without touching production code.

## 19. Phase 2 status

**Phase 2 has not been started.** No Angular/frontend code, no REST adapter, no
application-side Ollama or hosted-provider integration, and no other Phase 2 deliverable
exists in this repository. Slice 10 is an audit only and added no functionality.
