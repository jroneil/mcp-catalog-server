# MCP Catalog Platform — Phases 1 and 2 Implementation Plan

**Status:** Phase 1 accepted; Phase 2 in progress: Slice 11 complete; Slice 12 complete; Slices 13–17 not started
**Version:** 0.2  
**Date:** 2026-09-23  
**Source PRD:** `MCP_Catalog_Platform_PRD_v0.3.md`  
**Scope:** Phase 1 historical baseline and Phase 2 delivery plan

---

## Revision and acceptance boundary

[Implementation plan v0.1](MCP_Catalog_Platform_IMPLEMENTATION_PLAN_v0.1.md) remains unchanged as the historical Phase 1 plan. Slices 1–10 below are reproduced verbatim and are completed historical slices, not reopened work. Their original checklist boxes and prospective wording are retained as historical requirements; [Slice 10 validation](SLICE_10_VALIDATION.md) is the authoritative Phase 1 completion/acceptance boundary (184 tests passed). Historical Phase 1 prohibitions and stop conditions apply to Phase 1 only.

The Phase 2 plan and decision gates begin in section 8. The user subsequently approved D1 and completed Slice 11, then approved D3 and authorized Slice 12 only. Decisions explicitly marked open remain unresolved; Slices 13–17 have not started. No commits are authorized for Slice 12.

## 1. Purpose

This revision preserves the completed Phase 1 plan and adds the approved Phase 2 slice decomposition derived from PRD v0.3. Slices 11–12 are complete; later slices have not started. See [Slice 12 validation](SLICE_12_VALIDATION.md). See [Slice 11 validation](SLICE_11_VALIDATION.md).

The plan is intentionally narrow.

Phase 1 delivers:

- Java 21 Spring Boot backend
- Spring AI / MCP integration
- PostgreSQL
- Flyway
- Docker Compose
- realistic seed data
- `search_catalog`
- `get_catalog_item`
- Streamable HTTP
- health checks
- backend and MCP tests
- MCP Inspector validation
- one real local MCP host validation
- foundational documentation

Phase 1 explicitly does **not** include:

- Angular
- Ollama integration
- hosted LLM integration
- Python examples
- MCP resources
- MCP prompts
- STDIO
- production authentication
- production observability stack

During Phase 1, no Phase 2 or Phase 3 scaffolding was authorized unless required for a Phase 1 requirement. For Phase 2, only the work explicitly assigned to Slices 11–17 is in scope; Phase 3 remains excluded.

---

## 2. Execution Rules

The implementation agent shall follow these rules:

1. Work one slice at a time.
2. Do not begin the next slice until the current slice passes its gate.
3. Do not add features not required by the PRD or this plan.
4. Do not silently change MCP tool names, schemas, or result shapes once a slice is accepted.
5. Treat AI/MCP tool arguments as untrusted application input.
6. Keep MCP transport code outside the business/service layer.
7. Keep persistence logic inside repository/persistence code.
8. Use PostgreSQL for the real persistence path.
9. Do not substitute H2 for the primary development database.
10. Keep host-exposed HTTP ports loopback-bound by default.
11. Do not commit secrets.
12. Update documentation in the same slice that changes behavior.
13. If a slice breaks the accepted baseline and cannot be corrected cleanly, roll back that slice rather than patching around the failure.
14. Keep commits small enough that each accepted slice can be reverted independently.

---

## 3. Historical Phase 1 Pre-Implementation Decision Gate

Before coding begins, resolve and record the following decisions in `docs/ARCHITECTURE.md` or an ADR.

### Required decisions

- exact Spring Boot version
- exact Spring AI / MCP version
- Maven confirmed as build tool unless intentionally changed
- PostgreSQL version
- Flyway version
- PostgreSQL JDBC driver version
- Java base image
- backend HTTP port
- MCP endpoint path
- package root
- whether Testcontainers is mandatory for PostgreSQL integration tests
- local MCP host to use for final Phase 1 validation

### Gate

Do not start Slice 1 until:

- versions are pinned,
- the package root is chosen,
- the MCP transport approach is documented,
- the testing decision is documented.

### Rollback

Not applicable. This is a documentation/decision gate.

---

## 4. Historical Phase 1 Repository Shape

At the end of Phase 1, the repository should resemble:

```text
mcp-catalog-server/
├── README.md
├── docker-compose.yml
├── .env.example
├── .gitignore
├── docs/
│   ├── MCP_Catalog_Platform_PRD_v0.3.md
│   ├── Model_Context_Protocol_Enhanced_Java_Spring_Reference.md
│   ├── ARCHITECTURE.md
│   ├── IMPLEMENTATION_PLAN.md
│   └── TEST_PLAN.md
└── backend/
    ├── pom.xml
    ├── Dockerfile
    └── src/
        ├── main/
        │   ├── java/...
        │   └── resources/
        │       ├── application.yml
        │       └── db/migration/
        └── test/
            └── java/...
```

Do not add `frontend/` or `examples/python/` during Phase 1.

---

# Slice 1 — Backend Skeleton and Docker Baseline

## Goal

Create the smallest runnable Java 21 Spring Boot backend with Docker and PostgreSQL infrastructure, but no catalog behavior and no MCP tools yet.

## Work

Create:

- Maven project
- Java 21 configuration
- Spring Boot application entry point
- Spring Boot Actuator health endpoint
- backend `Dockerfile`
- root `docker-compose.yml`
- PostgreSQL service
- backend service
- `.env.example`
- `.gitignore`
- externalized database configuration
- dependency/version pinning
- basic startup documentation

Docker Compose must:

- start PostgreSQL,
- wait for PostgreSQL readiness appropriately,
- start the backend,
- bind host-exposed backend HTTP to `127.0.0.1` by default,
- avoid embedding real credentials in committed files.

## Tests / Checks

Required:

- Maven compile succeeds
- application context test succeeds
- backend container builds
- `docker compose up --build` starts PostgreSQL and backend
- health endpoint reports ready/healthy
- backend host mapping is loopback-bound, not `0.0.0.0`

## Gate

Slice 1 is accepted only when all checks pass from a clean checkout.

## Rollback

Revert Slice 1 commit(s) if the project cannot reliably start with Docker Compose.

---

# Slice 2 — PostgreSQL Schema, Flyway, and Seed Data

## Goal

Create the real database foundation before service or MCP logic.

## Work

Add:

- Flyway
- `catalog_item` schema
- immutable migration(s)
- realistic seed data
- JPA entity or chosen persistence model
- repository interface/implementation
- `PRODUCT` / `SERVICE` type handling

Minimum catalog fields:

```text
id
sku
name
type
description
price
active
created_at
updated_at
```

Seed data must include:

- active records
- inactive records
- products
- services
- multiple price ranges
- meaningful business names/descriptions

Avoid placeholder records such as `foo`, `bar`, or `test123`.

## Tests / Checks

Required:

- migrations run automatically on clean startup
- schema is created successfully
- seed rows are present
- repository can retrieve seeded rows
- PostgreSQL integration test exercises the real PostgreSQL path
- migration failure causes startup/test failure rather than being ignored

If Testcontainers was made mandatory in the decision gate, repository integration tests must use it here.

## Gate

Slice 2 is accepted only when the persistence path is proven against PostgreSQL.

## Rollback

Revert Slice 2 if schema/migration behavior is unstable or if the implementation bypasses Flyway.

---

# Slice 3 — Catalog Domain and Service Layer

## Goal

Implement business behavior independently of MCP.

## Work

Create:

- catalog domain model / application DTOs as appropriate
- `CatalogService`
- search criteria model
- paged result model
- validation rules
- repository query support

Implement Phase 1 search behavior:

- optional item type
- optional active status
- optional maximum price
- optional case-insensitive text search
- page number
- page size

Rules:

```text
default page: 0
default pageSize: 20
maximum pageSize: 100
```

Phase 1 text search shall use simple case-insensitive SQL matching.

PostgreSQL full-text search is out of scope.

Define explicit validation bounds for:

- search text length
- price values
- page number
- page size
- enum values

Implement detail retrieval by item identifier.

## Tests / Checks

Required service tests:

- no-filter search
- filter by type
- filter by active status
- filter by maximum price
- text search
- combined filters
- default pagination
- maximum page size
- invalid page
- invalid page size
- invalid price
- invalid enum where applicable
- item found
- item not found
- no unbounded result path

## Gate

Slice 3 is accepted only when the service layer is fully testable without MCP-specific logic.

## Rollback

Revert Slice 3 if business rules leak into transport code or persistence becomes coupled directly to MCP concerns.

---

# Slice 4 — MCP Server Wiring and Tool Discovery

## Goal

Introduce MCP infrastructure without implementing full tool behavior yet.

## Work

Add:

- Spring AI / MCP server configuration
- Streamable HTTP transport
- MCP server identity/version
- MCP adapter package
- tool registration mechanism
- local-safe HTTP exposure
- Origin validation or equivalent protection where supported by the selected stack

MCP-specific code must call the application/service layer rather than repositories directly.

## Tests / Checks

Required:

- application starts with MCP enabled
- MCP initialization succeeds
- MCP tool registry can be inspected
- MCP endpoint remains loopback-bound by default
- invalid/non-supported request origin behavior is documented and tested where the framework allows
- no catalog business logic exists in MCP configuration classes

## Gate

Slice 4 is accepted when MCP infrastructure is alive and inspectable without having changed catalog service behavior.

## Rollback

Revert Slice 4 if MCP wiring forces business logic into transport/configuration classes.

---

# Slice 5 — `search_catalog` MCP Tool

## Goal

Expose the accepted catalog search service as the first MCP tool.

## Tool Contract

Tool name:

```text
search_catalog
```

Inputs:

- optional type
- optional active
- optional max price
- optional text
- optional page
- optional page size

Defaults:

```text
page = 0
pageSize = 20
maximum pageSize = 100
```

Outputs shall be structured and predictable.

Tool metadata shall include:

- stable name
- clear model-readable description
- explicit parameter descriptions
- machine-readable schema

## Work

Implement MCP adapter mapping:

```text
MCP request
→ MCP input validation/mapping
→ CatalogService
→ paged result
→ MCP structured output
```

Do not duplicate service-layer filtering logic inside the tool.

## Tests / Checks

Required:

- tool is discoverable
- schema matches documented contract
- no-filter request works
- each optional filter works
- combined filters work
- pagination defaults work
- page size greater than 100 is rejected or constrained according to the chosen validation policy
- invalid values return useful errors
- tool cannot request an unbounded result set
- result data comes from PostgreSQL
- tool descriptions are meaningful to an AI client

## Contract Gate

Once Slice 5 is accepted:

- tool name,
- parameter names,
- parameter types,
- result shape

shall be treated as a versioned API contract.

Any later breaking change requires explicit review and documentation.

## Gate

Slice 5 is accepted only after automated tool tests pass and the tool can be invoked successfully with MCP Inspector.

## Rollback

Revert Slice 5 if it bypasses `CatalogService`, silently changes validation behavior, or produces unstable schemas.

---

# Slice 6 — `get_catalog_item` MCP Tool

## Goal

Expose catalog detail lookup through MCP.

## Tool Contract

Tool name:

```text
get_catalog_item
```

Input:

- catalog item identifier

Output:

- one structured catalog item

Not-found behavior must be explicit and non-fabricated.

## Work

Implement:

```text
MCP request
→ adapter
→ CatalogService
→ item or defined not-found error
```

## Tests / Checks

Required:

- tool is discoverable
- valid ID returns expected item
- unknown ID returns defined error behavior
- malformed ID is rejected
- no stack trace or secret appears in errors
- result comes from PostgreSQL
- MCP adapter does not call repository directly

## Contract Gate

Once accepted, the tool contract is treated as versioned.

## Gate

Slice 6 is accepted only after automated tests and MCP Inspector validation pass.

## Rollback

Revert Slice 6 if error behavior is ambiguous or business logic moves into the MCP adapter.

---

# Slice 7 — End-to-End MCP Integration Tests

## Goal

Prove the full path independently of a human-driven Inspector session.

## Work

Add integration coverage for:

```text
MCP client
→ Streamable HTTP
→ MCP tool
→ CatalogService
→ Repository
→ PostgreSQL
→ structured MCP response
```

At minimum test:

- server initialization
- tool discovery
- `search_catalog`
- `get_catalog_item`
- invalid search input
- item not found
- database-backed result correctness

## Tests / Checks

Required:

- integration suite can run repeatably
- PostgreSQL state is controlled for tests
- tests do not depend on an external AI provider
- tests do not require Ollama
- tests do not require Angular

## Gate

Slice 7 is accepted only when the MCP path is proven automatically end-to-end.

## Rollback

Revert Slice 7 changes if tests are flaky or require manual environment state.

---

# Slice 8 — MCP Inspector Validation

## Goal

Validate the server manually using MCP Inspector as required by the PRD.

## Work

Document exact steps for:

- starting Docker Compose
- locating the MCP endpoint
- connecting MCP Inspector
- listing tools
- inspecting schemas
- invoking `search_catalog`
- invoking `get_catalog_item`
- testing invalid arguments
- testing not-found behavior

Capture the expected request/response examples in documentation.

## Validation Scenarios

At minimum:

### Search

```text
type = SERVICE
active = true
maxPrice = 200
page = 0
pageSize = 20
```

### Detail

Use a known seeded catalog item.

### Failure

- invalid page size
- unknown catalog item

## Gate

Slice 8 is accepted only when a developer can reproduce all scenarios from the documentation.

## Rollback

Documentation-only rollback if instructions are incorrect; do not alter working code merely to fit stale documentation.

---

# Slice 9 — Real Local MCP Host Validation

## Goal

Prove interoperability with one real MCP-compatible host while keeping the server local.

## Work

Using the host selected in the pre-implementation decision gate:

- configure the host to connect to the local Streamable HTTP MCP server
- confirm tool discovery
- invoke `search_catalog`
- invoke `get_catalog_item`
- record required host configuration
- record any interoperability issue without changing the accepted tool contract unless explicitly approved

The MCP server shall not be exposed publicly to satisfy this requirement.

## Validation Scenario

Natural-language intent:

> Find active service items under $200.

The host should invoke the MCP tool and return results originating from the seeded PostgreSQL catalog.

## Gate

Slice 9 is accepted only when one real local MCP host can invoke at least one catalog tool successfully.

## Rollback

If host-specific configuration causes generic server behavior to regress, revert the host-specific server change and document the compatibility issue instead.

---

# Slice 10 — Phase 1 Documentation and Acceptance Audit

## Goal

Close Phase 1 against the PRD rather than against implementation assumptions.

## Work

Update:

- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/TEST_PLAN.md`

Document:

- pinned dependency versions
- startup commands
- package/layer architecture
- database migrations
- MCP endpoint
- loopback binding
- Origin handling
- tool contracts
- pagination
- validation bounds
- Inspector setup
- real-host setup
- test commands
- known limitations
- Phase 1 security limitations
- explicit Phase 1 exclusions

## Final Acceptance Checklist

Phase 1 is complete only when all are true:

- [ ] Java 21 backend builds
- [ ] critical dependency versions are pinned
- [ ] PostgreSQL is the real persistence path
- [ ] Flyway owns schema creation
- [ ] realistic seed data loads
- [ ] Docker Compose starts cleanly from a fresh checkout
- [ ] health endpoint reports ready
- [ ] host-exposed HTTP ports bind to `127.0.0.1` by default
- [ ] Origin protection is implemented/documented where supported
- [ ] `search_catalog` is discoverable
- [ ] `get_catalog_item` is discoverable
- [ ] both tools operate against PostgreSQL
- [ ] search pagination is bounded
- [ ] input validation is enforced
- [ ] AI/MCP arguments are treated as untrusted input
- [ ] tool contracts are documented as versioned APIs
- [ ] unit tests pass
- [ ] PostgreSQL repository integration tests pass
- [ ] MCP tool tests pass
- [ ] MCP end-to-end integration tests pass
- [ ] MCP Inspector can invoke both tools
- [ ] one real local MCP host can invoke a catalog tool
- [ ] errors are useful and do not expose stack traces/secrets
- [ ] no real secrets are committed
- [ ] Angular has not been added
- [ ] Ollama/hosted LLM integration has not been added
- [ ] Python examples have not been added
- [ ] MCP resources/prompts/STDIO have not been added
- [ ] documentation matches the implemented behavior

## Gate

Phase 1 is not complete until every applicable checklist item passes.

---

## 5. Historical Phase 1 Suggested Commit Boundaries

A clean execution sequence would be:

```text
01 - project skeleton + docker baseline
02 - postgres + flyway + seed data
03 - catalog service + validation + pagination
04 - MCP server wiring
05 - search_catalog tool
06 - get_catalog_item tool
07 - MCP integration tests
08 - Inspector documentation/validation
09 - real host validation
10 - Phase 1 documentation + acceptance audit
```

Each commit should leave the repository in a buildable/testable state.

---

## 6. Historical Phase 1 Stop Conditions

The implementation agent shall stop and ask for a decision rather than inventing behavior when:

- required dependency versions cannot be resolved cleanly,
- Spring AI/MCP APIs differ materially from the architecture assumption,
- Origin validation is not supported in the expected way,
- Streamable HTTP requires an architectural change,
- the selected real MCP host cannot connect using the planned transport,
- a tool-contract change would be breaking,
- a requirement conflicts with another accepted requirement,
- completing a slice would require Phase 2 or Phase 3 functionality,
- security behavior cannot be implemented as specified without broadening scope.

---

## 7. Historical Phase 1 Completion Statement

Phase 1 is complete when a developer can:

1. clone the repository,
2. run:

```bash
docker compose up --build
```

3. connect MCP Inspector,
4. discover both catalog tools,
5. query realistic catalog data stored in PostgreSQL,
6. receive bounded, validated, structured results,
7. connect one real local MCP host,
8. invoke a catalog tool successfully,

with host-exposed HTTP access loopback-bound by default and without requiring Angular, Ollama, hosted AI, Python, MCP resources, prompts, or STDIO.

---

## 8. Phase 2 scope, sequence and preservation rules

Phase 2 derives from PRD FR-7–FR-13A and supporting §§9–16, 19, 21–23. It adds a human-facing catalog UI, a REST adapter over the accepted service, and one natural-language catalog workflow using either local Ollama or one hosted provider. Slice 9's external-host validation is not application-side provider integration.

| Slice | Title | Status |
| --- | --- | --- |
| 11 | Read-only catalog REST adapter | Complete; see Slice 11 validation |
| 12 | Angular catalog search and Docker integration | Complete; see Slice 12 validation |
| 13 | Angular catalog item detail | Planned |
| 14 | Local Ollama catalog workflow — backend | Planned; AI decisions pending |
| 15 | Hosted provider and configuration-based selection | Planned; provider decisions pending |
| 16 | Natural-language catalog workflow in Angular | Planned |
| 17 | Phase 2 acceptance audit and documentation | Planned |

Sequence rationale: REST establishes a separately testable adapter before browser work. Search proves the browser-to-database path and detail completes the conventional UI. Local AI proves capability invocation and the local data path before hosted-provider differences are introduced. The AI UI then consumes the established workflow. Final acceptance verifies the complete phase.

### Rules applying to every Phase 2 slice

- Preserve accepted Phase 1 behavior, MCP tool names/schemas/result shapes, service rules, migrations, and local network/security defaults. Do not refactor working Phase 1 code unless the current slice requires it.
- Retain the 184-test Phase 1 regression baseline, with the explicitly approved architecture-test transition in section 9. Do not delete, disable, skip, evade or weaken coverage to make a slice pass.
- Keep controllers and MCP adapters thin; both use CatalogService. SQL stays in persistence. Provider orchestration stays separate from catalog business logic and MCP transport. No transport types enter catalog application APIs.
- Keep PostgreSQL/Testcontainers for persistence integration tests. Do not substitute H2.
- Run targeted tests during development and the full backend Maven gate before accepting implementation changes. Once frontend exists, run its applicable tests and production build as well.
- Verify Compose startup, health, loopback publication and private PostgreSQL whenever runtime configuration changes; perform the complete regression check at phase acceptance.
- Deterministic provider tests do not replace the real local and hosted demonstrations required by FR-13. Missing infrastructure or credentials leave the applicable gate incomplete.
- Pin newly introduced dependencies intentionally; do not broadly upgrade the accepted Boot/Spring AI/MCP baseline. Verify compatibility before adding provider or frontend tooling.
- Maintain documentation with behavior changes. Each slice produces `docs/SLICE_<number>_VALIDATION.md` containing objective, requirements, changed files, decisions, tests, exact commands/results, manual evidence, limitations, and explicit later-scope exclusions.
- Work one slice at a time. Do not start the next slice until the current gate passes. Do not waive failures or present placeholders as completed functionality.
- No speculative abstractions, new catalog write operations, full-text search, RAG, general chatbot or persistent conversation feature is assigned by this plan.
- Phase 3 exclusions remain intact: MCP resources/prompts/STDIO, standalone Java MCP-client example, Python examples, production authentication/authorization and production observability expansion. PRD §18 licensing remains a prerequisite for public distribution, not a new Phase 2 implementation slice.

## 9. Phase 2 decision gates

The slice decomposition is approved. The decisions below distinguish approval of scope from approval of a detailed interface or integration design. Record resolutions in ARCHITECTURE before the dependent implementation begins. Do not silently choose an unresolved product/architecture behavior.

| Gate | Due before | Status / required decision |
| --- | --- | --- |
| D1 — REST contract | Slice 11 | Approved by the user for Slice 11: exact contract below; preserve CatalogService semantics. |
| D2 — REST test transition | Slice 11 | Approved policy below; apply the enduring architecture-boundary coverage in Slice 11. |
| D3 — Frontend toolchain and routing | Slice 12 | Resolved by user approval: Angular 22.x stable (npm resolves core 22.2.0), matching CLI major (stable 22.1.8), Node 22.22.3, npm 10.9.8 with package-lock.json and npm ci; local port 4200, same-origin nginx and development proxy. See D3 details below and ARCHITECTURE. |
| D4 — AI capability invocation | Slice 14 | Open: in-process model tool adapter calling CatalogService versus internal MCP connection. FR-13 does not specify the mechanism; FR-17 places a standalone Java MCP client in Phase 3. Resolve the application path without assuming a Phase 3 deliverable. |
| D5 — AI workflow contract and limits | Slice 14 | Open: exact single catalog workflow, request/result format, backend endpoint, unsupported-intent behavior, execution limits/timeouts and UI presentation contract. Do not expand into general chat. |
| D6 — Local model and data path | Slice 14 | Open: primary Ollama model and available installation; model tool-call capability; container-to-host connectivity; evidence demonstrating no required hosted transmission of catalog/business prompt data. Prior external-host model success is not sufficient evidence for this application path. |
| D7 — Provider selection and startup | Slice 14; finalized in Slice 15 | Open: configuration keys, absent-provider behavior, and switching semantics. Configuration-based selection is required; hot switching is not. Preserve standalone Phase 1 operation and tests without provider credentials. |
| D8 — Hosted provider | Slice 15 | Open: first provider/model, compatible module and externally supplied credentials for live validation. Do not introduce an automatic hosted fallback. |
| D9 — Live workflow acceptance | Backend scenario before Slice 14; UI scenario before Slice 16 | Open: agreed demonstration inputs, expected database-grounded outcome and evidence for actual capability invocation in both modes. Mocks alone cannot close this gate. |

### D2: approved REST architecture-test transition

The Phase 1 test `McpArchitectureBoundaryTest.noRestControllerOrRequestMappedEndpointExistsInPhaseOne()` scans all compiled application classes and rejects REST-controller/request-mapping annotations. That historical absence assertion conflicts with FR-8 once REST is introduced.

Approved transition: replace this Phase-1-specific assertion with enduring architecture-boundary coverage. Retain an executable test and all Phase 1 behavioral/contract coverage; do not delete, skip or weaken the assertion's protection. The replacement must establish that:

- REST controller/request-mapping code exists only in the approved REST adapter package.
- REST catalog operations depend on CatalogService, not repositories or SQL/JDBC APIs.
- Catalog application/persistence remain free of web/servlet transport types; existing MCP/application/persistence boundaries remain enforced.
- MCP contracts and behavior are unchanged, with all other existing tests retained and passing.

Document the test-name/assertion transition explicitly in Slice 11 validation. Additional tests are expected; preserving an arbitrary count is not a substitute for preserving coverage. Do not move code outside the scan, switch endpoint mechanisms just to evade it, or introduce conditional skips. No Phase 1 production behavior is changed by this test-scope transition.

### D1: approved Slice 11 REST contract

- `GET /api/v1/catalog`: optional `type`, `active`, `maxPrice`, `text`, `page`, `pageSize`; HTTP 200 JSON containing `items`, `page`, `pageSize`, `totalItems`, `totalPages`.
- `GET /api/v1/catalog/{id}`: accepted detail behavior, including inactive-item lookup.
- Preserve service defaults, bounds, deterministic ordering and not-found semantics.
- Approved HTTP mapping: 200 success, 400 malformed/invalid input, 404 absent detail, sanitized 500 unexpected failure.
- Errors contain exactly `status`, `error`, `message`, `path`; no stack traces, exception classes, SQL, secrets or implementation diagnostics. HTTP binding may reject malformed values before service invocation. No duplicated service validation or MCP DTO dependency.

FR-8 fixes the base path and shared service; the user approved the detailed contract above. D1 and D2 are resolved for Slice 11. Later AI/frontend decisions remain deferred.

### D3: approved Slice 12 frontend toolchain and routing

- Angular 22.x stable, standalone components and signals for local state; no application NgModules. Current stable npm resolution is core/compiler 22.2.0, CLI/build 22.1.8 (matching major, compatible published peer ranges). No prerelease packages.
- Node 22.22.3, npm 10.9.8, package-lock.json and npm ci for reproducible builds; no yarn/pnpm/bun or Node 24/26.
- Development server binds 127.0.0.1:4200; Angular development proxy forwards /api to http://127.0.0.1:8080.
- Application API URLs are relative /api/v1/catalog, with no backend hostname in components/services.
- Production build uses node:22.22.3-bookworm-slim; static runtime uses pinned nginx:1.30.5-alpine3.24, SPA fallback and /api/ reverse proxy to the existing mcp-catalog-server:8080 Compose service.
- Frontend publication defaults to 127.0.0.1:4200; backend publication and private PostgreSQL remain unchanged.
- Same-origin routing needs no CORS additions. Do not proxy /mcp or alter its Origin/Host protections. No frontend secrets or AI-provider configuration.

## 10. Phase 2 PRD traceability

| PRD requirement | Assigned slices | Acceptance evidence |
| --- | --- | --- |
| FR-7 Angular list/search, detail, filters, readable results | 12–13 | Browser search and detail against REST/PostgreSQL; focused frontend tests |
| FR-8 Versioned REST under `/api/v1/catalog` | 11 | Approved REST contract, HTTP tests and live requests |
| FR-9 Shared service/data for REST and MCP | 11; final audit 17 | Equivalent requests return equivalent data through CatalogService |
| FR-10 Local externally configured Ollama | 14; UI demonstration 16 | Real local application workflow, external settings |
| FR-11 Hosted provider and configuration-based selection | 15–16 | Same workflow using one real hosted provider; no source changes to switch |
| FR-12 Local-only catalog/business prompt data path | 14, 16–17 | Documented and observed local path; no required hosted processing |
| FR-13 Model interprets, invokes capability, returns matching items in UI with both modes | 14–16 | Actual capability invocation and grounded UI results in both modes |
| FR-13A Same-origin/proxy preference; allowlisted CORS if enabled | 12, 16–17 | Browser routing tests and configuration inspection |
| §9 Provider separation / Spring AI abstractions | 14–15 | Dependency and boundary tests/review; no speculative provider framework |
| §10 Angular standalone structure, REST, version pinning | 11–12, 14–15 | Builds, dependency pins and architecture review |
| §§11–12 Three-service Compose startup, secret handling, readiness | 12 onward; 17 | Compose startup and browser retrieval; credentials absent from source/assets/logs |
| §13 AC-1–AC-8 | All | Adapter/service/persistence/provider boundaries; model arguments validated |
| §14 Safe REST/application errors | 11, 14–16 | Validation/not-found/failure tests; no fabricated successful results |
| §15 Phase 2 browser policy | 12, 16 | Same-origin/proxy or explicit CORS allowlist; MCP protection preserved |
| §16 REST, frontend, manual provider tests | 11–17 | Per-slice tests and real-mode demonstrations |
| §19 Documentation | Every slice; 17 | Current startup, architecture, test and provider instructions |
| §§21–22 Phase 2 exit criteria/DoD | 17 | Complete acceptance checklist and reproducible evidence |
| §23 scenarios B, C and D; consistency with accepted A | 11–17 | Manual search, local AI, hosted AI and shared catalog results |

---

# Slice 11 — Read-only Catalog REST Adapter

## Objective and PRD requirements

Expose catalog search and detail under `/api/v1/catalog`, using the same CatalogService and persisted data as MCP. Addresses FR-8, FR-9, AC-2–AC-5, §14 and §16 REST tests.

## Deliverables and expected files/components

- Search/detail HTTP endpoints, request binding, result mapping and safe HTTP error translation under the D1-approved contract.
- New `com.example.mcpcatalog.rest` adapter package and corresponding tests.
- Approved D2 replacement architecture-boundary assertion plus REST-specific boundary coverage.
- README, ARCHITECTURE, TEST_PLAN and `docs/SLICE_11_VALIDATION.md` updates.
- No service/repository production changes anticipated.

## Architecture boundaries

Controllers delegate to CatalogService. HTTP parsing/error translation stays in REST; service validation stays authoritative. No controller repository access, duplicated filters/defaults, or dependency on MCP-facing DTOs.

## Tests required

Each filter and combined filters; pagination/defaults; malformed parameters; service validation; known/missing detail; safe unexpected errors; PostgreSQL-backed REST/MCP consistency; D2 boundary tests; all retained Phase 1 regressions.

## Acceptance gates

D1 recorded and D2 implemented without weakened coverage; full Maven clean verify passes; live REST requests satisfy the contract; MCP names/schemas/results unchanged; Compose startup, health, loopback binding and private PostgreSQL remain correct.

## Exclusions / deferred work

No Angular, AI providers/workflow, writes, new catalog rules, schema change or MCP rework. Browser deployment starts in Slice 12.

## Dependencies

Accepted Phase 1 at Slice 10 and resolution of D1. D2 policy is already approved.

## Stop conditions

Stop if REST requires unapproved service/MCP behavior changes, duplicated business logic, weakened tests, or an unresolved HTTP contract. Common Phase 2 stop conditions also apply.

---

# Slice 12 — Angular Catalog Search and Docker Integration

## Objective and PRD requirements

Provide a usable browser catalog list/search over REST. Addresses FR-7 list/search/filter/results, FR-13A, §§10–12 and frontend tests in §16.

## Deliverables and expected files/components

- Standalone Angular application; catalog list, text/type/active/maximum-price controls, bounded page navigation, readable results and empty/error states.
- `frontend/` application, API client, search components/tests, pinned tooling and lockfile.
- Development proxy and frontend Docker/proxy configuration; additive root Compose frontend service.
- Necessary configuration placeholders and startup documentation; Slice 12 validation record.

## Architecture boundaries

Browser -> REST -> existing service. Angular owns presentation and interaction, not catalog filtering rules. Prefer same-origin deployment and a development proxy; no wildcard CORS or changes to MCP origin protection. Provider credentials never belong in frontend assets.

## Tests required

Filter-to-request mapping, list rendering, pagination, empty/error states; browser-to-REST smoke against real PostgreSQL; frontend tests and production build; retained backend regression suite.

## Acceptance gates

D3 resolved; Compose starts frontend/backend/database; browser searches persisted records; development proxy works; local exposure and private PostgreSQL preserved; frontend/backend gates pass.

## Exclusions / deferred work

Detail screen in Slice 13; AI in Slices 14–16. No write/admin operations, provider selection UI or extra catalog features.

## Dependencies

Slice 11 and D3 toolchain/routing decisions.

## Stop conditions

Stop if browser access requires broadening MCP exposure, wildcard CORS, duplicated catalog rules or unresolved toolchain incompatibility. Common stop conditions apply.

---

# Slice 13 — Angular Catalog Item Detail

## Objective and PRD requirements

Complete the conventional catalog UI with item detail. Addresses FR-7 detail view and §16 navigation/error tests.

## Deliverables and expected files/components

Frontend detail component/route, result-to-detail navigation, API-client extension if needed, readable missing-item/request-failure states, focused tests and documentation/validation record.

## Architecture boundaries

Use the accepted REST detail endpoint. No browser database access, MCP calls or replicated lookup policy.

## Tests required

Navigation from results, known detail, missing item, request failure, return navigation; frontend build/tests and backend regression gate.

## Acceptance gates

A user can search and inspect real catalog records; required tests/builds pass; accepted REST and MCP behavior remains intact.

## Exclusions / deferred work

No editing/deletion or additional catalog capabilities. AI workflow starts in Slice 14.

## Dependencies

Slices 11–12 accepted.

## Stop conditions

Stop if detail requires undocumented API changes or write capabilities. Common stop conditions apply.

---

# Slice 14 — Local Ollama Catalog Workflow — Backend

## Objective and PRD requirements

Enable a locally configured model to interpret a catalog request and invoke catalog capability. Addresses FR-10, FR-12, backend portion of FR-13, §9 and AC-8.

## Deliverables and expected files/components

- Externalized Ollama connection/model settings and one bounded catalog AI workflow.
- Backend interface needed by the later UI, with documented local data path and failure behavior.
- Separate backend AI configuration/orchestration package and workflow HTTP adapter; exact design follows D4–D7.
- Compatible provider dependency in `backend/pom.xml`, application settings, `.env.example` placeholders, tests and documentation/validation record.

## Architecture boundaries

Provider orchestration stays outside catalog business logic and MCP transport. Model arguments reach existing service validation. Use Spring AI abstractions where sufficient; no speculative provider framework. Do not expose a new MCP tool merely to implement this workflow.

## Tests required

Controlled model/tool-call tests, invalid generated arguments, provider failures, PostgreSQL-grounded results, and evidence that local mode does not send catalog/business prompts to a hosted provider. Full backend regression gate remains independent of live providers.

## Acceptance gates

D4–D7 and backend portion of D9 resolved; real Ollama execution invokes the catalog capability and returns matching persisted records; deterministic tests/regressions pass. Record actual local data flow. This slice does not claim the human-readable UI requirement is complete.

## Exclusions / deferred work

Hosted provider in Slice 15; Angular AI interaction in Slice 16. No general chatbot, conversation history, RAG or standalone Java MCP-client example. Ollama may remain outside Compose as allowed by §11; optional full-stack Ollama Compose is not assigned here.

## Dependencies

Slice 11's backend adapter foundation and D4–D7/D9 decisions; Slices 12–13 precede this work in the approved delivery sequence.

## Stop conditions

Stop on unresolved invocation architecture, inability of the selected model to perform the operation, local mode requiring hosted processing, unavailable live evidence or an unapproved dependency upgrade. Common stop conditions apply.

---

# Slice 15 — Hosted Provider and Configuration-Based Selection

## Objective and PRD requirements

Support one hosted provider through the same workflow. Addresses FR-11, provider-selection portion of FR-13, §9 and §11 secret handling.

## Deliverables and expected files/components

Selected provider integration, external credentials/model settings, configuration selection between local and hosted modes; AI provider configuration and necessary compatible dependency; configuration placeholders, selection tests and setup/validation documentation.

## Architecture boundaries

Provider selection does not alter catalog services, persistence, MCP contracts or workflow semantics. Credentials stay backend-side. Custom provider abstractions require a demonstrated need beyond Spring AI's existing abstractions.

## Tests required

Selection/configuration errors, safe failures, local mode without hosted credentials, same workflow with controlled provider responses; actual hosted-provider demonstration; full regressions.

## Acceptance gates

D7 finalized and D8/D9 hosted requirements resolved; real hosted workflow succeeds; switching requires configuration only, no source edits; local mode still meets FR-12; all required tests pass.

## Exclusions / deferred work

No additional hosted providers, automatic fallback or runtime provider-selection UI. Frontend AI interaction remains Slice 16.

## Dependencies

Slice 14; selected provider/model and credentials for live verification.

## Stop conditions

Stop if live validation cannot complete, credentials would reach browser assets/logs, or provider differences require changing accepted catalog/MCP contracts. Common stop conditions apply.

---

# Slice 16 — Natural-Language Catalog Workflow in Angular

## Objective and PRD requirements

Complete FR-13: interpret a natural-language request, invoke catalog capability and present matching persisted items in a human-readable UI, using both provider modes. Complete the FR-12 local-path demonstration; preserve FR-13A browser policy.

## Deliverables and expected files/components

One natural-language input/result interaction, loading/error presentation and readable catalog results; frontend workflow components/API client/tests and demo instructions. Backend interface adjustments only within the accepted workflow contract. Slice 16 validation record includes both real-mode demonstrations.

## Architecture boundaries

Browser calls backend workflow, never provider APIs directly. Provider orchestration/credentials remain server-side. Catalog facts originate from the catalog capability, not model invention.

## Tests required

Submission, pending/result/error states; controlled end-to-end tests; actual UI demonstrations with local Ollama and the selected hosted provider; frontend build/tests and backend regressions.

## Acceptance gates

D9 UI criteria resolved; same catalog scenario works in both modes with evidence of invocation and database-grounded results; local data-path claims match observation; all required tests/builds pass.

## Exclusions / deferred work

No general chat, persistent conversations, additional workflows or streaming unless separately justified and approved. No new provider-selection UI is assigned. Final phase audit follows in Slice 17.

## Dependencies

Slices 12–15 and approved workflow/UI contract.

## Stop conditions

Stop on fabricated results, mock-only provider evidence, leaked secrets or a required workflow expansion. Common stop conditions apply.

---

# Slice 17 — Phase 2 Acceptance Audit and Documentation

## Objective and PRD requirements

Close Phase 2 against FR-7–FR-13A, §19 documentation, §21 exit criteria, §22 DoD and §23 scenarios.

## Deliverables and expected files/components

Requirement-to-evidence matrix, reproducible startup/provider instructions, architecture/data-flow documentation and final acceptance record. Update plan status, README, ARCHITECTURE, TEST_PLAN and `docs/SLICE_17_VALIDATION.md`. No new product capability.

## Architecture boundaries

Audit the delivered design rather than redesigning accepted layers/contracts. Keep historical Slice 1–10 validation records unchanged.

## Tests required

Full backend/frontend gates; Compose startup; REST/MCP data consistency; catalog UI behavior; both real-provider workflows and local-path evidence. Existing recorded live evidence may be used only where still applicable to the final implementation; identify its provenance.

## Acceptance gates

Every applicable Phase 2 requirement has concrete evidence; all retained Phase 1 regressions pass; the checklist below is satisfied; no unresolved failed gate or Phase 3 scope. No completion claim based only on mock providers.

## Exclusions / deferred work

No feature expansion, hardening project or dependency modernization. Phase 3 remains unstarted.

## Dependencies

Slices 11–16 accepted.

## Stop conditions

Stop on missing evidence, failed tests or unmet requirements. Return defects to their owning slice; do not waive the gate or conceal scope changes.

---

## 11. Phase 2 acceptance checklist

These boxes record future acceptance work, not current completion.

- [ ] FR-7: Angular list/search, type/active/maximum-price filters, readable results and item detail work against real data.
- [ ] FR-8: Versioned search/detail REST endpoints under `/api/v1/catalog` satisfy the approved contract.
- [ ] FR-9: Equivalent REST/MCP requests use CatalogService and return consistent persisted data.
- [ ] FR-10: Application works with an externally configured local Ollama installation/model.
- [ ] FR-11: At least one hosted provider works, with configuration-only selection and no catalog/MCP coupling to its vendor.
- [ ] FR-12: Local mode requires no hosted transmission of catalog/business prompt data; documented claims match the implemented and observed path.
- [ ] FR-13: One concrete natural-language catalog workflow invokes catalog capability and presents matching results in the UI with both real provider modes.
- [ ] FR-13A: Same-origin deployment/development proxy is used, or any enabled cross-origin access has an explicit allowlist without wildcard CORS.
- [ ] Frontend builds and tests pass; backend full Maven gate passes, including all retained Phase 1 coverage and the approved D2 transition.
- [ ] PostgreSQL/Testcontainers integration, REST validation/not-found/errors, frontend filters/detail/errors and provider workflow tests pass.
- [ ] Compose starts frontend/backend/PostgreSQL and the browser retrieves catalog data via REST; health/readiness remains correct.
- [ ] Existing backend loopback mapping, unpublished PostgreSQL and MCP Origin/Host protections remain intact; any new frontend host publication is local-only.
- [ ] MCP tool contracts, accepted service behavior and immutable migrations remain unchanged.
- [ ] New dependency/toolchain versions are pinned and compatible; no unapproved broad upgrade.
- [ ] Provider secrets remain external/backend-side and absent from source, frontend assets and logs; sensitive prompt/business data is not logged by default.
- [ ] README, architecture, test plan and per-slice validation records describe implemented behavior, exact commands and known limitations.
- [ ] No placeholders are claimed as complete; no failed or skipped required gate is waived.
- [ ] No unassigned write operations, speculative abstractions or Phase 3 functionality was added.

## 12. Phase 2 stop and completion rules

Stop and report unresolved requirement conflicts, unsafe scope expansion, required breaking MCP changes, inability to preserve architecture boundaries, unapproved dependency incompatibility, failed tests or unavailable required live verification. Revert the current unaccepted slice if it destabilizes the accepted baseline and cannot be corrected within scope; never rewrite historical migrations or move business rules into adapters to force a pass.

Phase 2 is complete only when Slice 17 establishes every applicable checklist item and PRD §21 Phase 2 exit criterion. Plan publication alone does not start Slice 11. Phase 1 remains complete and accepted at [Slice 10](SLICE_10_VALIDATION.md).
