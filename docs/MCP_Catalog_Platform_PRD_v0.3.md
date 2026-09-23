# Product Requirements Document (PRD)
## MCP Catalog Platform

**Status:** Draft for external review  
**Version:** 0.3  
**Date:** 2026-09-23  
**Primary implementation:** Java 21 / Spring Boot / Spring AI / MCP  
**Reference implementation:** Minimal Python MCP examples  
**Primary database:** PostgreSQL  
**Frontend:** Angular (Phase 2)  
**Deployment:** Docker / Docker Compose from project inception

---

## 1. Purpose

The MCP Catalog Platform is a reference implementation and learning project for exposing enterprise application capabilities to AI systems through the Model Context Protocol (MCP).

The project will demonstrate how a conventional Spring Boot business application can expose the same underlying business services through:

1. a standard REST interface for human-facing applications, and
2. an MCP interface for AI clients and assistants.

The system will begin with a small catalog domain so the MCP concepts remain clear and testable, while the overall architecture remains representative of a real enterprise application.

The project is intended to be:

- technically realistic rather than a toy demo,
- understandable to non-technical stakeholders through a visual frontend,
- usable with local AI models through Ollama,
- configurable to use hosted AI providers when desired,
- structured to demonstrate MCP portability through minimal Python examples in a later phase,
- structured so it can later serve as a reusable reference for modernization and AI-integration work.

---

## 2. Background

MCP provides a standardized way for AI applications to connect to external tools, data, and reusable prompts. The core model is host, client, server, and transport. The initial focus of this project is MCP server development in Java using Spring Boot and Spring AI, with Streamable HTTP as the primary transport.

The first use case is catalog search because it is easy to understand, easy to validate, and closely resembles how an enterprise system may expose business data to an AI assistant.

---

## 3. Product Vision

Demonstrate a clean enterprise pattern in which AI access is another adapter over an existing business-service layer rather than a separate application with duplicated rules.

```text
                         ┌──────────────────┐
                         │   Angular UI     │
                         └────────┬─────────┘
                                  │ REST
                                  ▼
                         ┌──────────────────┐
                         │ REST Adapter     │
                         └────────┬─────────┘
                                  │
┌──────────────────┐              │
│ MCP Client / AI  │              │
│ Host / Inspector │              │
└────────┬─────────┘              │
         │ MCP                    │
         ▼                        │
┌──────────────────┐              │
│ MCP Adapter      │              │
└────────┬─────────┘              │
         │                        │
         └────────────┬───────────┘
                      ▼
             ┌──────────────────┐
             │ CatalogService   │
             └────────┬─────────┘
                      ▼
             ┌──────────────────┐
             │ Repository       │
             └────────┬─────────┘
                      ▼
             ┌──────────────────┐
             │ PostgreSQL       │
             └──────────────────┘
```

Business logic must remain below the transport/adapter layer.

---

## 4. Goals

### 4.1 Primary Goals

The project shall:

- provide a working Java/Spring MCP server,
- expose useful catalog operations through MCP,
- use PostgreSQL from the beginning,
- run through Docker Compose from the beginning,
- use Flyway for database schema management,
- support Streamable HTTP for MCP,
- provide realistic seeded catalog data,
- support local AI through Ollama,
- allow a hosted AI provider to be selected through configuration,
- provide a visual Angular frontend in Phase 2,
- demonstrate that REST and MCP use the same service layer,
- provide automated tests at service, repository, MCP, and integration levels,
- include minimal Python MCP examples in Phase 3 showing protocol portability.

### 4.2 Learning Goals

The project shall provide hands-on experience with:

- MCP host/client/server roles,
- tools,
- resources,
- prompts,
- JSON-RPC-based communication,
- MCP capability discovery,
- Streamable HTTP,
- MCP Inspector,
- Spring AI MCP integration,
- schema and tool-description design,
- client interoperability,
- error handling,
- authentication/authorization concepts,
- observability,
- local vs hosted AI models,
- MCP-specific security concerns including untrusted tool arguments, prompt/tool-description injection risks, client trust, and local HTTP exposure.

---

## 5. Non-Goals

The first release is not intended to be:

- a full production catalog-management system,
- an e-commerce application,
- a replacement for an ERP or inventory platform,
- a complete AI agent framework,
- a full Python duplicate of the Java application,
- a multi-tenant SaaS platform,
- a production-ready security implementation in Phase 1,
- a benchmarking platform for comparing LLM quality,
- a vector database or RAG project.

Authentication, richer MCP primitives, additional transports, and broader enterprise hardening will be implemented in later phases.

---

## 6. Users and Actors

### 6.1 Developer / Learner
Needs simple startup, clear architecture, inspectable requests and responses, automated tests, realistic examples, and documentation.

### 6.2 Technical Stakeholder
Needs understandable architecture, a visual frontend, a local AI option, demonstrable shared business logic, and clear separation between application data and AI-provider concerns.

### 6.3 Business Stakeholder
Needs recognizable catalog data, natural-language queries, visible search results, and confidence that local AI can be used when external data transmission is undesirable.

### 6.4 MCP Host / Client
Needs discoverable tools, understandable schemas, predictable responses, useful error messages, and stable transport.

---

## 7. Core Domain

The initial domain will be a simple catalog supporting both products and services.

### 7.1 Catalog Item

Minimum fields:

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

### 7.2 Type

Initial values:

```text
PRODUCT
SERVICE
```

### 7.3 Seed Data

The application shall include believable business-oriented seed data with active and inactive records, products and services, and multiple price ranges. Seed data should avoid meaningless placeholders such as `foo`, `bar`, or `test123`.

---

## 8. Functional Requirements

### 8.1 Phase 1 — MCP Foundation

#### FR-1 — Catalog Search Tool
The MCP server shall expose a `search_catalog` tool with optional criteria for:

- item type,
- active status,
- maximum price,
- case-insensitive text search,
- page number,
- page size.

Phase 1 text search shall use simple case-insensitive SQL matching. PostgreSQL full-text search is out of scope for Phase 1.

Pagination defaults:

```text
page: 0
pageSize: 20
maximum pageSize: 100
```

The server shall reject invalid pagination requests and shall not allow an unbounded result set.

Input validation shall include sensible bounds for:

- search-text length,
- price values,
- page number,
- page size,
- enum values.

Example intent:

> Find all active service items under $200.

The tool shall return only records matching the supplied filters.

#### FR-2 — Catalog Item Detail Tool
The MCP server shall expose a `get_catalog_item` tool using a catalog item identifier. If the record is not found, the server shall return a clear structured error or MCP-compatible failure response rather than fabricate data.

#### FR-3 — MCP Tool Metadata
Each tool shall provide a descriptive tool name, clear tool description, explicit parameter descriptions, machine-readable input schema, and predictable structured output. Descriptions shall be written for AI consumption as well as human readability.

#### FR-3A — MCP Tool Contract Stability
MCP tool names, argument schemas, and result shapes shall be treated as versioned API contracts.

Breaking changes to MCP tool contracts shall require explicit version consideration and documentation rather than silent schema mutation.

#### FR-4 — Streamable HTTP
The MCP server shall support Streamable HTTP as the initial network transport.

Phase 1 shall be local-only by default:

- host-exposed MCP/REST ports shall bind to loopback (`127.0.0.1`) by default,
- Docker configuration shall not unintentionally publish the service on all host interfaces,
- Origin validation or an equivalent request-origin protection shall be enabled for local HTTP MCP access where supported,
- any configuration that broadens network exposure shall be explicit and documented.

#### FR-5 — MCP Inspector Compatibility
The server shall be testable using MCP Inspector. Documentation shall include startup, connection, tool discovery, successful invocation, and failure-case instructions.

#### FR-6 — Real MCP Host Validation
At least one real MCP-compatible host shall successfully connect to the locally running server and invoke a catalog tool.

This requirement does not require public internet exposure of the MCP server. The specific host is not permanently fixed by the PRD.

### 8.2 Phase 2 — Human-Facing Demo and AI Provider Integration

#### FR-7 — Angular Frontend
A lightweight Angular frontend shall be added as a demonstration/admin interface. It shall provide a catalog list/search screen, item detail view, type filter, active/inactive filter, maximum price filter, and readable results.

#### FR-8 — REST API
The Spring Boot application shall expose versioned REST endpoints under `/api/v1/catalog`. The REST API shall use the same `CatalogService` as the MCP adapter.

#### FR-9 — Shared Business Logic
Equivalent REST and MCP requests shall operate over the same persisted data and business service.

```text
Angular → REST → CatalogService → PostgreSQL
AI Host → MCP → CatalogService → PostgreSQL
```

#### FR-10 — Local Ollama Support
The application shall support a locally hosted Ollama instance as an AI provider. Ollama shall be configurable by environment variables or externalized configuration.

Example concept:

```text
AI_PROVIDER=ollama
OLLAMA_BASE_URL=http://host.docker.internal:11434
OLLAMA_MODEL=<configured-model>
```

Exact property names may follow Spring AI conventions.

#### FR-11 — Hosted AI Provider
The application shall support at least one hosted AI provider in addition to Ollama. Provider selection shall be configuration-driven without requiring source-code changes. The application shall not be tightly coupled to one hosted vendor.

#### FR-12 — Local-Only Data Path
When configured to use local Ollama, the application shall not require catalog/business prompt data to be transmitted to a hosted AI provider. Documentation shall distinguish local processing, local model inference, and hosted-provider operation. The project shall not make privacy claims beyond what the implemented network architecture can demonstrate.

#### FR-13 — AI Demonstration
Phase 2 shall implement one concrete natural-language catalog workflow.

Example:

> Show me active service items under $200.

The configured AI model shall interpret the request, invoke the catalog capability, and return matching catalog items in a human-readable UI.

The same workflow shall be demonstrable with:

- local Ollama, and
- at least one configured hosted AI provider.

#### FR-13A — Browser Origin Policy
The frontend/backend architecture shall prefer same-origin deployment and a development proxy during local Angular development.

If cross-origin browser access is enabled, CORS shall use an explicit allowlist and shall not default to `*`.

### 8.3 Phase 3 — Expanded MCP Capabilities

#### FR-14 — MCP Resource
Add at least one read-only MCP resource, for example `catalog://items/{id}`.

#### FR-15 — MCP Prompt
Add at least one reusable MCP prompt relevant to catalog work, such as catalog analysis, comparison, or item summary.

#### FR-16 — STDIO Transport
Add STDIO support to demonstrate local-process MCP operation. Streamable HTTP remains the primary network transport.

#### FR-17 — Java MCP Client
Create a minimal Java MCP client capable of initialization, tool discovery, tool invocation, and result handling.

#### FR-18 — Python Reference Examples
Provide a minimal Python MCP server and client to demonstrate portability. They are not required to duplicate PostgreSQL persistence, Angular integration, or the complete Java architecture.

#### FR-19 — Authentication and Authorization
Before any production-oriented deployment, define and implement authentication and authorization appropriate to the selected deployment model, including tool-level access where appropriate.

#### FR-20 — Observability
Production-oriented phases shall provide structured logs, MCP invocation diagnostics, health endpoints, correlation/request identifiers where useful, and metrics where useful. Sensitive prompt or business information shall not automatically be written to logs.

---

## 9. AI Provider Architecture

AI-provider selection shall remain separate from catalog business logic and MCP transport logic.

```text
Application
   ├── Local Ollama
   └── Hosted AI Provider
```

Spring AI's existing model abstractions should be used where sufficient. A custom application-level provider abstraction shall be added only if actual provider-specific behavior requires it.

---

## 10. Technical Requirements

### Backend
- Java 21
- Spring Boot
- Spring AI / MCP integration
- Spring Data
- PostgreSQL
- Flyway
- Maven unless a later decision explicitly selects Gradle
- explicit version pinning for Spring Boot, Spring AI/MCP, PostgreSQL driver, Flyway, and other critical runtime dependencies

Dependency upgrades shall be intentional and reviewed because MCP and Spring AI APIs evolve quickly.

### Frontend
- Angular in Phase 2
- standalone/component-oriented modern Angular structure
- REST communication to backend
- no duplicated backend business rules

### Database
PostgreSQL shall be used from project inception. H2 shall not be the main development database. Database changes shall be managed through immutable Flyway migrations.

Initial migration pattern:

```text
V1__create_catalog_item.sql
V2__seed_catalog_items.sql
```

### Search Implementation
Phase 1 text search shall use simple case-insensitive SQL matching.

PostgreSQL full-text search is out of scope for Phase 1 and may be evaluated later if the catalog domain grows enough to justify it.

### API Versioning
REST endpoints begin under `/api/v1/`. MCP server/application version begins at `0.1.0`.

MCP tool names, argument schemas, and result shapes are versioned API contracts. Breaking changes require explicit version consideration and corresponding documentation.

---

## 11. Docker and Local Development

The project shall be Dockerized from the beginning.

### Phase 1 Services

```text
mcp-catalog-server
postgres
```

### Phase 2 Services

```text
frontend
mcp-catalog-server
postgres
```

Ollama may remain outside the Compose stack when using an existing local installation. A later optional `docker-compose.full.yml` may include Ollama for users who want a self-contained demo.

### Network Exposure
Phase 1 Docker Compose configuration shall be local-development oriented.

Host port mappings for MCP/REST shall bind to loopback (`127.0.0.1`) by default. Any configuration that exposes the service beyond the local machine shall be explicit and documented.

### Secrets and Configuration
Secrets and environment-dependent configuration shall not be hard-coded.

Use:

- environment variables,
- `.env` only for local development where appropriate,
- `.env.example` with placeholders only,
- environment-injected secrets or an external secret mechanism for non-local deployments.

Hosted-AI credentials shall never be embedded in source code, committed configuration, frontend assets, or logs. Real API keys shall not be committed.

### Health Checks
Health checks shall be defined where practical, and the backend shall not assume PostgreSQL is usable solely because its container process has started.

---

## 12. Startup Acceptance Requirement

From a clean checkout, the project shall support:

```bash
docker compose up --build
```

For Phase 1, successful startup means:

1. PostgreSQL starts and becomes healthy.
2. Spring Boot starts.
3. Flyway migrations execute.
4. Catalog schema exists.
5. Seed data is available.
6. Application health reports ready.
7. MCP endpoint is available.
8. MCP Inspector can discover configured tools.

Phase 2 additionally requires the Angular frontend to start and retrieve catalog data through REST.

---

## 13. Architecture Constraints

- **AC-1:** MCP is an adapter; MCP-specific code shall not contain core catalog business logic.
- **AC-2:** REST is an adapter; controllers shall not contain core business logic.
- **AC-3:** Both adapters use the same application/service layer.
- **AC-4:** Repositories encapsulate persistence-specific behavior.
- **AC-5:** Transport-specific request/response objects should not unnecessarily leak into domain/service APIs.
- **AC-6:** MCP tool inputs and outputs shall be explicit and intentionally designed.
- **AC-7:** Prefer maintainability and obvious boundaries over excessive abstraction.
- **AC-8:** AI-generated MCP tool arguments are untrusted application input and shall be validated to the same standard as REST or direct user input.

---

## 14. Error Handling

Expected application errors include item not found, invalid filters, invalid price range, and unsupported enum values. Unexpected failures include database unavailability, serialization failure, and transport failures.

Errors exposed through MCP and REST shall be useful to callers, avoid stack traces and secrets, and never fabricate successful results.

---

## 15. Security Requirements

### Phase 1

Phase 1 is for local/reference use, but local-only does not mean unsecured.

Phase 1 shall:

- avoid committed secrets,
- use externalized configuration,
- avoid logging API keys or sensitive prompt/business data,
- avoid unnecessary credential exposure,
- bind host-exposed MCP/REST access to loopback by default,
- validate MCP tool arguments as untrusted input,
- enforce input bounds and pagination limits,
- use Origin validation or equivalent protection where supported for local HTTP MCP access,
- document clearly that Phase 1 is not production-hardened.

### Phase 2

Phase 2 browser access shall prefer same-origin deployment or an Angular development proxy.

If cross-origin browser access is enabled, CORS shall use an explicit allowlist and shall not default to `*`.

### Later Phases

Production hardening shall consider authentication, authorization, OAuth or supported MCP authorization flows, TLS, credential rotation, least privilege, tool-level authorization, rate limiting, audit logging, and sensitive-data controls.

---

## 16. Testing Strategy

Tests are required gates.

### Unit Tests
Cover catalog service filtering, validation, mapping, and error behavior.

### Repository Integration Tests
Cover PostgreSQL persistence, filter queries, not-found behavior, and migrations. Testcontainers should be considered so tests exercise PostgreSQL rather than an incompatible substitute.

### MCP Tool Tests
Cover tool registration, input schemas, valid and invalid invocation, and structured responses.

### MCP Integration Tests
Cover server startup, client initialization, tool discovery, tool invocation, and correct data returned from PostgreSQL.

### REST Tests
Phase 2 shall cover catalog search, detail retrieval, validation, not found, and consistency with the shared service layer.

### Frontend Tests
Phase 2 shall include focused tests for filters, results display, detail navigation, and error states.

### Manual Compatibility Tests
Document and validate MCP Inspector, one real MCP host, Ollama mode, and at least one hosted-provider mode.

---

## 17. Observability and Diagnostics

At minimum, the application shall provide a Spring Boot health endpoint, meaningful startup logs, database migration visibility, and MCP connection/invocation diagnostics suitable for development. Later phases may add structured logging, correlation, metrics, and tracing. Prompt contents and sensitive business data shall not be logged by default.

---

## 18. Licensing

The repository license is not required to begin Phase 1.

Before public distribution or use as a reusable external reference, the repository shall include an explicit license.

Until then:

```text
License: TBD before public distribution
```

---

## 19. Documentation Requirements

The repository shall contain:

```text
README.md
docs/
  PRD.md
  ARCHITECTURE.md
  IMPLEMENTATION_PLAN.md
  TEST_PLAN.md
```

Documentation shall include prerequisites, Docker startup, MCP Inspector setup, Ollama configuration, hosted-provider configuration, architecture overview, tool inventory, testing commands, and known limitations.

---

## 20. Proposed Repository Structure

Phase 1 begins with:

```text
mcp-catalog-platform/
├── README.md
├── docker-compose.yml
├── .env.example
├── docs/
│   ├── PRD.md
│   ├── ARCHITECTURE.md
│   ├── IMPLEMENTATION_PLAN.md
│   └── TEST_PLAN.md
└── server-java/
    ├── pom.xml
    └── src/
        ├── main/
        └── test/
```

Phase 2 adds:

```text
frontend/
└── angular application
```

Phase 3 adds:

```text
examples/
└── python/
    ├── minimal_server.py
    └── minimal_client.py
```

The Phase 2 and Phase 3 directories shall not be scaffolded empty in Phase 1 unless an implementation decision specifically requires it.

---

## 21. Delivery Phases

### Phase 1 — MCP Foundation

Deliver:

- Java 21 Spring Boot application
- Spring AI/MCP configuration
- PostgreSQL
- Flyway
- Docker Compose
- realistic seed data
- `search_catalog`
- `get_catalog_item`
- Streamable HTTP
- health checks
- backend/MCP tests
- MCP Inspector validation
- successful connection from one real MCP host
- foundational documentation

**Phase 1 explicit exclusions:**

- Angular
- Ollama integration
- hosted LLM integration
- Python examples
- MCP resources
- MCP prompts
- STDIO
- production authentication
- production observability stack

**Phase 1 exit criteria:** clean Docker startup succeeds, both tools are discoverable, both operate against PostgreSQL, automated tests pass, Inspector can invoke both tools, one real local MCP host can invoke a tool, host-exposed ports bind to loopback by default, input bounds and pagination limits are enforced, MCP tool contracts are documented, and failure cases return useful errors.

### Phase 2 — Demo UI and AI Provider Choice

Deliver:

- Angular frontend
- versioned REST API
- shared `CatalogService`
- Ollama support
- configurable hosted-provider support
- visual search and detail screens
- concrete natural-language catalog workflow demonstrated with Ollama and one hosted provider
- REST/frontend tests
- local-only AI configuration documentation

**Phase 2 exit criteria:** a business user can visually search catalog data, the defined natural-language catalog workflow works through the configured AI provider, UI and MCP operate on consistent underlying data, provider switching does not require code changes, Ollama mode works with an existing local installation, and hosted-provider mode works with documented configuration.

### Phase 3 — Advanced MCP and Enterprise Hardening

Deliver selectively:

- MCP resources
- MCP prompts
- STDIO
- minimal Java MCP client
- minimal Python server/client examples
- authentication/authorization
- improved observability
- additional security hardening
- operational documentation

Phase 3 exit criteria shall be refined before implementation because security and deployment requirements depend on the selected target environment.

---

## 22. Definition of Done

A feature is done when requirements are implemented, relevant tests pass, error cases are covered, no secrets are committed, Docker behavior remains valid, documentation is updated, and architecture boundaries remain intact.

Project-level initial Definition of Done:

> A developer can clone the repository, run `docker compose up --build`, connect MCP Inspector, discover the catalog tools, query realistic catalog data stored in PostgreSQL, and receive correct structured results without manually configuring the database. Host-exposed Phase 1 ports bind to loopback by default, input limits are enforced, and the MCP tool contracts are documented.

Phase 2 adds:

> A non-technical stakeholder can use the Angular application to view the same catalog and can see a working AI-assisted demonstration using either local Ollama or a configured hosted AI provider.

---

## 23. Key Demonstration Scenarios

### Scenario A — MCP Catalog Search

User asks through an MCP-compatible AI host:

> Find active service items under $200.

Flow:

```text
AI Host
→ MCP client
→ search_catalog
→ CatalogService
→ PostgreSQL
→ structured results
→ AI host
```

### Scenario B — Human UI Search

User selects:

```text
Type: SERVICE
Active: Yes
Max Price: $200
```

Flow:

```text
Angular
→ REST
→ CatalogService
→ PostgreSQL
→ UI results
```

The records shall be consistent with Scenario A.

### Scenario C — Local AI

When configured for Ollama, model requests are sent to the configured local Ollama endpoint and no hosted AI provider is required.

### Scenario D — Hosted AI

When configured for a supported hosted provider, provider selection is configuration-driven, catalog/MCP code does not need to change, and hosted credentials are externally configured.

---

## 24. Risks

- **R-1 — MCP/Spring AI APIs change quickly.** Mitigate by pinning dependencies, documenting versions, isolating MCP integration, and reviewing release notes.
- **R-2 — Project expands beyond the learning goal.** Mitigate through phase delivery and PRD-controlled scope.
- **R-3 — AI provider abstraction becomes overengineered.** Mitigate by using Spring AI abstractions first.
- **R-4 — Frontend distracts from MCP.** Frontend starts only after Phase 1 exit criteria are met.
- **R-5 — Privacy claims exceed technical reality.** Document actual data flows and avoid unverified guarantees.
- **R-6 — Demo works only against mock paths.** MCP, REST, and tests use the real shared service and PostgreSQL path.

---

## 25. Open Questions

These do not block Phase 1 PRD approval:

1. Which exact Spring Boot and Spring AI versions will be pinned?
2. Maven or Gradle? Maven is the current default assumption.
3. Which hosted AI provider will be implemented first?
4. Which Ollama model will be used for the primary demo?
5. Which MCP-compatible host will be used for formal Phase 1 validation?
6. Testcontainers is the preferred default for PostgreSQL integration tests; confirm whether it should be mandatory.
7. At what point should OAuth/authentication be introduced?
8. Should the optional full Docker Compose configuration include Ollama?
9. What exact MCP resource and prompt should be selected for Phase 3?
10. Which license should be selected before public distribution?

---

## 26. Success Criteria

The project is successful if it demonstrates that:

- MCP can expose real Spring business functionality cleanly.
- MCP-specific code remains separate from domain/business logic.
- PostgreSQL-backed application data can be accessed consistently through REST and MCP.
- Docker provides a reproducible environment.
- MCP Inspector and a real local host can call the server.
- Phase 1 host-exposed HTTP access is bounded by loopback-first safe defaults.
- MCP tool contracts are explicit and treated as versioned APIs.
- A human-facing Angular UI makes the system understandable to non-technical users.
- Local Ollama provides a practical alternative to hosted AI.
- Hosted AI can be enabled without redesigning the application.
- Minimal Python examples demonstrate that MCP is protocol-based rather than Java-specific.
- The repository is useful as a reference for future enterprise modernization and AI-integration work.
