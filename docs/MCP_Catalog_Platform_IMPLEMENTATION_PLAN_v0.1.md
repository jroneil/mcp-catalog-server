# MCP Catalog Platform — Phase 1 Implementation Plan

**Status:** Draft for execution  
**Version:** 0.1  
**Date:** 2026-09-23  
**Source PRD:** `MCP_Catalog_Platform_PRD_v0.3.md`  
**Scope:** Phase 1 only — MCP Foundation

---

## 1. Purpose

This implementation plan turns Phase 1 of the MCP Catalog Platform PRD into small, test-gated delivery slices.

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

No Phase 2 or Phase 3 scaffolding should be added unless it is required to support a Phase 1 requirement.

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

## 3. Pre-Implementation Decision Gate

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

## 4. Proposed Phase 1 Repository Shape

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

## 5. Suggested Commit Boundaries

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

## 6. Stop Conditions

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

## 7. Phase 1 Completion Statement

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
