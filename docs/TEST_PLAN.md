# MCP Catalog Platform — Phase 1 test plan

**Status:** Phase 1 complete (Slices 1–10)
**Source requirements:** PRD v0.3 §16 (Testing Strategy), §12 (Startup Acceptance), §14 (Error Handling), §15 (Security); Implementation Plan v0.1 §Slice 10
**Scope:** Phase 1 only — the implemented and passing test suite. This document records what exists; it does not propose new tests.

---

## 1. How to run the tests

Host prerequisites: Java 21, Maven 3.9+, and a running Docker daemon accessible to
Testcontainers (PostgreSQL integration tests are not skipped when Docker is absent; they
fail).

```bash
# Full gate: compile, unit tests, PostgreSQL/Testcontainers integration tests, packaging
mvn -f backend/pom.xml --batch-mode --no-transfer-progress clean verify

# A single suite
mvn -f backend/pom.xml --batch-mode --no-transfer-progress test -Dtest=PhaseOneEndToEndMcpTest

# Runtime acceptance (Docker Compose)
docker compose up --build --wait --wait-timeout 300
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/readiness
```

Docker image builds deliberately skip test execution (`-DskipTests`); the Maven gate
above is the authoritative test run.

**Current result: `clean verify` → BUILD SUCCESS, 184 tests, 0 failures, 0 errors, 0 skips.**

---

## 2. Test types and environments

| Type | Environment | Testcontainers | Notes |
| --- | --- | --- | --- |
| Service unit tests | Plain JUnit 5 + Mockito, no Spring | No | Validation, defaults, mapping, error policy |
| Persistence integration | PostgreSQL 18.6 in Testcontainers | Yes | Real SQL, constraints, Flyway history |
| Service integration | PostgreSQL 18.6 in Testcontainers | Yes | Real query/pagination behaviour through the service |
| MCP tool unit | Plain JUnit 5 + Mockito | No | Schema/contract, delegation, mapping, error propagation |
| MCP integration | Spring Boot `RANDOM_PORT` + PostgreSQL container | Yes | Real MCP Java SDK client over Streamable HTTP |
| Architecture boundary | Plain JUnit 5 reading compiled classes | No | Layering enforced from bytecode constant pools |
| Container acceptance | Docker Compose | n/a | Startup, health, exposure, MCP endpoint |

No H2 is used anywhere; `com.h2database` is absent from the dependency tree. No external
AI provider, Ollama, Inspector, or manual database preparation is required by the test
suite.

---

## 3. Suite inventory (184 tests)

| Suite | Cases | Covers |
| --- | ---: | --- |
| `McpCatalogApplicationTest` | 1 | Context start, real JDBC connectivity, HTTP health/readiness |
| `CatalogPersistenceTest` | 5 | Flyway V1/V2 applied and validated, seed retrieval, identity, constraints |
| `FlywayStartupFailureTest` | 1 | Startup fails on an invalid migration; PostgreSQL rolls back |
| `CatalogServiceTest` | 25 | Search defaults/validation/limits/pagination arithmetic, detail errors |
| `CatalogServicePostgresTest` | 23 | Real filters, case-insensitive text, ordering, bounded pagination, not-found |
| `SearchCatalogToolTest` | 13 | `search_catalog` mapping, schema contract, no duplicated rules, boundaries |
| `GetCatalogItemToolTest` | 12 | `get_catalog_item` mapping, schema contract, not-found/invalid propagation |
| `OptionalArgumentsToolCallbackTest` | 3 | Absent MCP `arguments` payload normalisation |
| `SanitizingToolCallbackTest` | 10 | Unexpected-failure sanitisation; intended messages preserved |
| `McpTransportSecurityTest` | 9 | Origin/Host allowlist semantics |
| `McpServerWiringTest` | 10 | Server identity, capabilities, tool registry, `/mcp` route, Origin/Host over HTTP |
| `McpToolDiscoveryTest` | 2 | Discovery mechanics with a test-scoped probe tool |
| `SearchCatalogMcpIntegrationTest` | 20 | `search_catalog` end to end over Streamable HTTP against seeded PostgreSQL |
| `GetCatalogItemMcpIntegrationTest` | 12 | `get_catalog_item` end to end over Streamable HTTP |
| `PhaseOneEndToEndMcpTest` | 23 | Whole Phase 1 path, contract freeze, security/transport regressions |
| `McpInternalFailureSanitizationTest` | 7 | Injected internal failure through the real MCP client |
| `McpArchitectureBoundaryTest` | 8 | Adapter/layer boundaries from compiled classes |
| **Total** | **184** | |

---

## 4. Mapping to PRD §16 testing strategy

| PRD §16 expectation | Phase 1 implementation |
| --- | --- |
| Unit tests: service filtering, validation, mapping, error behaviour | `CatalogServiceTest` (25) |
| Repository integration tests against PostgreSQL, migrations, not-found | `CatalogPersistenceTest`, `CatalogServicePostgresTest`, `FlywayStartupFailureTest` (Testcontainers) |
| MCP tool tests: registration, input schemas, valid and invalid invocation, structured responses | `SearchCatalogToolTest`, `GetCatalogItemToolTest`, `McpToolDiscoveryTest`, `McpServerWiringTest` |
| MCP integration tests: server startup, client initialisation, tool discovery, invocation, correct data from PostgreSQL | `SearchCatalogMcpIntegrationTest`, `GetCatalogItemMcpIntegrationTest`, `PhaseOneEndToEndMcpTest`, `McpInternalFailureSanitizationTest` |
| REST tests | Out of Phase 1 scope — no REST adapter exists |
| Frontend tests | Out of Phase 1 scope — no frontend exists |
| Manual compatibility tests: MCP Inspector, one real MCP host | Recorded in `SLICE_8_VALIDATION.md` (Inspector 2.7.0) and `SLICE_9_VALIDATION.md` (opencode 1.18.23 + local Ollama). Manual by nature; not part of the automated gate. Ollama/hosted-provider *application* modes are Phase 2. |

---

## 5. Error-case coverage

| Case | Expected | Where |
| --- | --- | --- |
| Invalid page size / page / price / type / oversized text | Tool error carrying the `CatalogService` message; no results | `CatalogServiceTest`, `SearchCatalogMcpIntegrationTest`, `PhaseOneEndToEndMcpTest` |
| Unknown catalog item | `Catalog item not found: <id>`, no fabricated item | `CatalogServiceTest`, `GetCatalogItemToolTest`, `GetCatalogItemMcpIntegrationTest` |
| Non-positive identifier | `Catalog item ID must be positive` | same as above |
| Missing / wrong-typed argument | Rejected by MCP schema validation before the handler | `GetCatalogItemMcpIntegrationTest`, `PhaseOneEndToEndMcpTest` |
| Unexpected internal failure | Fixed sanitised message; no SQL, URL, path, credential or stack frame | `SanitizingToolCallbackTest`, `McpInternalFailureSanitizationTest` |
| Migration failure | Application startup fails; no partial schema | `FlywayStartupFailureTest` |
| Hostile `Origin` / `Host` | HTTP 403 / 421 before JSON-RPC handling | `McpTransportSecurityTest`, `McpServerWiringTest`, `PhaseOneEndToEndMcpTest` |

---

## 6. Manual/acceptance scenarios (not automated)

Documented with exact commands and outputs in the corresponding validation records:

- MCP Inspector connection, tool discovery, schema inspection and invocation — `SLICE_8_VALIDATION.md`
- Real MCP host with a local Ollama model answering natural-language prompts — `SLICE_9_VALIDATION.md`
- Clean Docker startup, exposure and data-integrity checks — `SLICE_1`–`SLICE_10_VALIDATION.md`

---

## 7. Known limitations of the Phase 1 suite

- The suite requires Docker (Testcontainers) for the PostgreSQL-backed tests.
- Docker image builds skip tests; run the Maven gate to execute them.
- MCP Inspector and real-host interoperability are validated manually and are not part
  of `clean verify`.
- Tool result schemas are asserted structurally, not against a stored JSON Schema
  document, because Spring AI 2.0.1's `ToolCallback` conversion does not expose
  `outputSchema`.
- Phase 2 REST, frontend and AI-provider tests do not exist and are out of scope.
