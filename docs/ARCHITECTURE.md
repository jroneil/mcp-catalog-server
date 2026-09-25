# MCP Catalog Platform architecture

Decision gate resolved 2026-09-23. Planning reference: [implementation plan v0.2](MCP_Catalog_Platform_IMPLEMENTATION_PLAN_v0.2.md), covering Phases 1 and 2. Slices 1–10 are complete and accepted; Phase 2 Slice 11 adds the approved REST adapter; Slice 12 adds Angular search; Slice 13 adds item detail; Slice 14 local workflow is accepted; Slice 15 hosted acceptance is on hold; Slice 16 adds the local-only assistant UI; Slice 17 has not started. The PRD v0.3 governs product requirements; the reference guide's alternative slice numbering does not govern delivery.

## Pinned decisions

| Component | Decision |
| --- | --- |
| Java | Java 21, compiled with release 21 |
| Spring Boot | 4.1.1 (parent, starters, plugins and managed dependency baseline) |
| Spring AI | 2.0.1 BOM; `spring-ai-starter-mcp-server-webmvc` activated in Slice 4 |
| MCP Java SDK | 2.0.0, matching Spring AI 2.0.1's published dependency; `mcp-core` and `mcp-json-jackson3` now on the runtime classpath |
| Build | Maven 3.9.16 in Docker; Maven 3.9+ and Java 21 for host builds |
| PostgreSQL | 18.6, official `postgres:18.6-trixie` image |
| Flyway | 12.4.0, Boot-aligned pin; core and PostgreSQL support added in Slice 2 |
| PostgreSQL JDBC | 42.7.13, Boot-aligned pin |
| Testcontainers | 2.0.5, mandatory for PostgreSQL integration tests; no H2 substitute |
| Java runtime image | `eclipse-temurin:21.0.12_8-jre-noble` |
| Build image | `maven:3.9.16-eclipse-temurin-21-noble` |
| HTTP | Container port 8080; host `127.0.0.1:8080` by default; host port configurable |
| Package root | `com.example.mcpcatalog` |
| Application version | 0.1.0 |
| MCP endpoint | `http://127.0.0.1:8080/mcp` |
| MCP transport | Spring MVC, synchronous server, stateful Streamable HTTP (`protocol=STREAMABLE`, `type=SYNC`); no legacy SSE transport or STDIO |
| Real-host validation | Completed in Slice 9 with opencode 1.18.23 + local Ollama `qwen3-coder-next:latest` (all four natural-language scenarios grounded). Claude Code 2.1.209 also connects over HTTP but is unauthenticated in this environment |

## Compatibility evidence

Verified against published releases, not tutorial versions:

- [Spring AI compatibility](https://docs.spring.io/spring-ai/reference/getting-started.html): 2.0.x supports Boot 4.0.x/4.1.x.
- [Spring AI 2.0.1 source POM](https://github.com/spring-projects/spring-ai/blob/v2.0.1/pom.xml): Boot 4.1.1.
- [Published Spring AI MCP 2.0.1 POM](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-mcp/2.0.1/spring-ai-mcp-2.0.1.pom): SDK 2.0.0. The reference's SDK 2.0.1 example is not imposed over the framework's tested dependency.
- [Boot 4.1.1 dependency BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom): Flyway, JDBC and Testcontainers versions above.
- [Spring AI MCP starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html): WebMVC Streamable HTTP and synchronous operation supported.
- [Claude Code HTTP MCP configuration](https://code.claude.com/docs/en/mcp): `claude mcp add --transport http catalog http://127.0.0.1:8080/mcp` is the supported registration. Slice 9 verified that Claude Code 2.1.209 connects to `/mcp` and completes the MCP `initialize` handshake (protocol 2025-11-25) with the default security posture; the host's natural-language tool use could not be exercised because that installation has no credentials. This is host/MCP integration only — not backend AI-provider integration.
- Official image catalogs: [Java](https://github.com/docker-library/official-images/blob/master/library/eclipse-temurin), [Maven](https://github.com/docker-library/official-images/blob/master/library/maven), [PostgreSQL](https://github.com/docker-library/official-images/blob/master/library/postgres).

Exact release tags are intentional; image tags can receive upstream OS rebuilds. Dependency/image upgrades require review. No milestone, snapshot, or latest tags are used.

## Slice 1 runtime

```text
Host 127.0.0.1:8080 -> Spring MVC / Actuator -> JDBC DataSource -> PostgreSQL:5432
```

Only health is exposed through Actuator. Health/readiness includes database connectivity and hides details. PostgreSQL has no published host port. Compose waits for `pg_isready` over TCP before starting the backend; the backend health check independently verifies a real JDBC connection through Actuator. A named volume persists PostgreSQL data at `/var/lib/postgresql` (PostgreSQL 18 image layout).

Host execution defaults to a loopback listener. Compose explicitly sets the listener to `0.0.0.0` **inside the container** so port forwarding works; the host publication remains hardcoded to `127.0.0.1`. Changing the host port does not widen binding. Credentials are required environment configuration without committed defaults. Local `.env` files are ignored. Spring SQL script initialization remains disabled; Flyway now exclusively creates and seeds the catalog table as described below.

## Later boundaries and gates

MCP adapter -> CatalogService -> repository -> PostgreSQL. The Slice 11 REST adapter reuses the same service. Blocking persistence is consistent with synchronous MCP and MVC.

Flyway 12.4.0 is active in Slice 2 and owns all schema changes. Slice 4 activates the WebMVC Streamable HTTP starter and `/mcp`; the MCP adapter package and its request-origin protection are described below. The Slice 4 gate was reached with an explicit Origin/Host allowlist implemented through the SDK validator rather than assumed from transport defaults. No production authentication is implied.

PostgreSQL integration tests must use Testcontainers and fail if Docker is unavailable; no silent skipping. The existing context test now also runs Flyway against its isolated PostgreSQL container before verifying health and JDBC connectivity. The MCP server must remain private. Real-host validation was completed in Slice 9 with a local Ollama-backed host (opencode 1.18.23); a Claude Code installation would additionally need its own account credentials.

The PRD's complete Phase 1 startup acceptance (schema, seed, MCP tools) is now met by the two registered catalog tools, Slice 7 proved the whole path end to end, Slice 8 validated the running server with MCP Inspector, and Slice 9 completed real-host validation with a local Ollama-backed host. `backend/` follows the implementation plan and explicit task, replacing the PRD's illustrative `server-java/` layout. Slices 1–10 are implemented and Phase 1 is complete at the [Slice 10 acceptance boundary](SLICE_10_VALIDATION.md); Phase 2 follows v0.2; Slice 11 adds REST, Slice 12 adds Angular search, Slice 13 adds detail, and Slices 14–17 remain unstarted.

## Slice 2 persistence decisions

Spring Data JDBC is the chosen persistence model, consistent with the PRD's Spring Data requirement and the plan's allowance for alternatives to JPA. The Boot 4.1.1 managed starter supplies its exact dependency baseline. Slice 2 introduced no Hibernate, auto-DDL, H2, service or transport layer; Slice 3 adds the application service described below. `CatalogItem`, `CatalogItemType` and `CatalogItemRepository` live under `com.example.mcpcatalog.catalog.persistence`.

- Flyway uses the existing PostgreSQL `public` schema explicitly; the configured database user owns the created catalog objects. The local Compose user also performs migration, consistent with the existing local baseline. Separate migration/runtime roles are future deployment hardening.
- `V1__create_catalog_item.sql` creates `public.catalog_item`; `V2__seed_catalog_items.sql` inserts 24 deterministic records. Applied migrations must never be edited; all future changes use a new versioned migration. No automatic baseline, repair, clean or alternate initialization path is enabled. Migration or validation failures abort context startup.
- ID: `BIGINT GENERATED BY DEFAULT AS IDENTITY`, mapped to Java `Long`. Seeds explicitly use IDs 1–24 and restart the identity at 25. ID and unique SKU indexes are sufficient for this slice; no speculative search indexes.
- SKU: unique, case-sensitive `VARCHAR(64)`, nonblank. Name: nonblank `VARCHAR(200)`. Description: nonblank `TEXT`. These are storage decisions, not a finalized service input-validation contract.
- Type: `VARCHAR(16)` with a `PRODUCT`/`SERVICE` check constraint; Java enum maps by name. A PostgreSQL enum is unnecessary for this small extensible set.
- Price: nonnegative `NUMERIC(12,2)` (up to 9,999,999,999.99), mapped to `BigDecimal`; zero is permitted for the initial consultation. No currency conversion or multicurrency behavior is introduced. PostgreSQL numeric scale can round extra fractional digits; Slice 3 service validation rejects inputs with more than two decimal places.
- Active: non-null boolean, defaults true. All nine required fields are non-null.
- Timestamps: `TIMESTAMP WITH TIME ZONE`, mapped to `OffsetDateTime`; comparisons use instants. Defaults initialize both on insert, with `updated_at >= created_at` enforced. There is no write API or update trigger: future update code must explicitly maintain `updated_at`. Seeds use fixed UTC timestamps.
- At the Slice 2 baseline, the repository extended the narrow Spring Data `Repository` interface with only `findById`, `findBySku` and `count`. Missing lookups return `Optional.empty()`. No save/delete or unbounded list is exposed; Slice 3 adds bounded query mechanics through a repository fragment.
- Seeds: 12 products and 12 services; each type has 9 active and 3 inactive rows. Prices span 0.00–1249.00. Stable examples are ID 1 / `PRD-101` (Ergonomic Wireless Mouse, 39.95) and ID 16 / `SVC-104` (Network Health Assessment, 199.00).

Tests verify actual Flyway history, no pending migrations, repeat migration without duplicate seeds, every seeded row's mapping, known and missing lookups, type/status coverage, identity continuation and database constraints. An isolated test-only V3 creates a probe then divides by zero: application startup must fail with a Flyway cause and PostgreSQL must roll back the probe table. The fixture is under test resources and never packaged into the runtime JAR.

Boot integration reference: [Flyway initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html). The Boot Flyway starter and the separate PostgreSQL database module are both present; merely adding Flyway core would not establish the Boot 4 integration.

## Slice 3 application behavior

`catalog.application.CatalogService` is the application entry point for search and detail. It owns defaults, input validation, orchestration, not-found behavior and mapping to annotation-free application results. `CatalogSearchCriteria`, `CatalogItemView` and `CatalogPage` carry no HTTP, MCP or Spring Data types. Type input/output uses the stable names `PRODUCT` and `SERVICE`; the service validates input before converting to the persistence enum. No transport is activated.

### Validation and defaults

Bounds are named constants in `CatalogService` so changes remain explicit and reviewable:

| Input | Policy |
| --- | --- |
| criteria | Null means all optional fields omitted |
| type | Null means both; otherwise exactly PRODUCT or SERVICE, case-sensitive, no whitespace coercion |
| active | Null means both; false selects inactive, true selects active |
| maxPrice | Null means no price filter; otherwise inclusive 0–9,999,999,999.99, BigDecimal scale <= 2; excess fractional places are rejected, not rounded (including 1.000) |
| text | Null/blank means no filter; maximum 200 Java UTF-16 code units checked before stripping outer whitespace; reject NUL, which PostgreSQL text cannot store |
| page | Default 0; accepted range 0–10,000 inclusive |
| pageSize | Default 20; accepted range 1–100 inclusive; invalid values rejected rather than clamped |
| detail id | Non-null positive Long; a valid but absent ID throws CatalogItemNotFoundException |

The 200-character text cap bounds query input; the page cap bounds offset work for this reference catalog. Price bounds align with the immutable NUMERIC(12,2) schema. BigDecimal excludes NaN/infinity; parsing nonnumeric transport input belongs to future adapters. Invalid application input raises `InvalidCatalogCriteriaException` with a field and a safe explanation, without echoing arbitrary text. These are application errors, not finalized MCP error contracts.

### Search and pagination

Filters combine with AND. Text means a literal case-insensitive substring of SKU, name **or** description. Blank text is omitted; `%`, `_`, and the chosen SQL escape character `!` are escaped for literal matching. Inputs are bound parameters, never SQL fragments. SQL uses PostgreSQL ILIKE only; there is no full-text search, ranking or fuzzy matching.

The minimal `CatalogItemSearch` / `CatalogItemSearchImpl` Spring Data repository fragment builds predicate SQL and applies the same predicates to a count and a data query. Every data query has required `LIMIT` and `OFFSET`; the service always supplies a validated limit <=100 and calculates the offset with long arithmetic. No unpaged service overload or repository listing operation exists. Existing ID/SKU lookup and count remain internal persistence capabilities; catalog application callers use the service.

Ordering is fixed `id ASC`: the unique primary key provides a total order without ties or dependence on names, prices or database plan order. Pages are stable for unchanged data. Search runs in a read-only REPEATABLE_READ transaction so count and rows share one snapshot. Separate page requests do not hold a shared snapshot; future concurrent writes could shift offset pages, but no write operations are introduced here.

`CatalogPage` contains immutable `items`, zero-based `page`, `pageSize`, `totalItems`, and `totalPages` (ceiling division; zero for zero matches). A valid page beyond the result set is empty and retains total metadata. `CatalogItemView` includes all nine catalog fields and has no persistence annotations. No separate domain engine is needed for these read-only behaviors.

V1/V2, seed data, dependency pins, Dockerfile, Compose networking and health configuration remain unchanged. Service unit tests verify policy without Spring/SQL; PostgreSQL/Testcontainers service tests verify real query behavior and bounded pagination, including more than 100 matches using rolled-back test fixtures.

## Slice 4 MCP adapter

MCP is an adapter, not a second application. All MCP code lives under `com.example.mcpcatalog.mcp`. Later catalog tools live there too and call `CatalogService`; nothing under `mcp` may reach a repository directly, and no MCP-specific request/response type may appear in `catalog.application`. Slice 4 added configuration only — no catalog business logic, and no tool.

### Transport and identity

`spring.ai.mcp.server` selects a synchronous Streamable HTTP server (no SSE, no STDIO, no stateless mode) served by Spring MVC. Identity is `mcp-catalog-server` version `0.1.0`, matching the application version. Only the tool capability is advertised; `resource`, `prompt` and `completion` are explicitly disabled because MCP resources, prompts and completions are Phase 3 scope, and the `@McpTool` annotation scanner is disabled because Slice 4 registers tools through Spring AI `ToolCallback` beans. The endpoint is `/mcp` (GET/POST/DELETE), matching the PRD and the decision gate.

Tool registration uses Spring AI's existing `ToolCallback`/`ToolCallbackProvider` bean conversion to `SyncToolSpecification`; no custom registrar is introduced. Slice 4 proved the mechanism with a test-scoped `ToolCallback` bean; Slices 5 and 6 register the two production tools through the same conversion and the same `ToolCallbackProvider` bean (see below). Catalog tool contracts are versioned APIs: names, argument schemas and result shapes change only with explicit review and documentation.

### Slice 5 tool: `search_catalog`

`mcp.tools.SearchCatalogTool` is the adapter. It is constructed with `CatalogService` only, passes every argument through unchanged (`null` for omitted inputs), and maps `CatalogPage` to the MCP-facing `SearchCatalogResult`/`SearchCatalogItem`. It owns no defaults, bounds, filtering, ordering, pagination arithmetic, SQL or repository access, so Slice 3's validation and defaulting stay authoritative. `InvalidCatalogCriteriaException` propagates untouched; the framework turns any thrown exception into an MCP tool error (`isError: true`) whose text is the exception message.

The tool name, description and input schema are generated by Spring AI from the annotated adapter method and are treated as a versioned contract:

- name: `search_catalog`
- inputs, all optional: `type` (string), `active` (boolean), `maxPrice` (number), `text` (string), `page` (integer), `pageSize` (integer)
- `required: []`, `additionalProperties: false`

**Bounds and enums are deliberately absent from the schema.** The MCP SDK validates incoming arguments against the schema before the handler runs (`ToolInputValidator`, `validateToolInputs=true` by default), so encoding `enum`/`minimum`/`maximum`/`maxLength` would reject out-of-range values with a generic schema error and would relocate the rule out of `CatalogService`. Instead the bounds appear in the parameter descriptions so a model can avoid them, while `CatalogService` remains the only enforcing authority. Adapters added later must follow the same rule.

**Result delivery is framework-determined.** Spring AI 2.0.1's `McpToolUtils.toSyncToolSpecification` builds the MCP `Tool` without `outputSchema` and returns only text content, with no `structuredContent`. The result contract is therefore a single JSON document in `content[0].text`: `{items:[{id,sku,name,type,description,price,active,createdAt,updatedAt}],page,pageSize,totalItems,totalPages}`, with timestamps as ISO-8601 UTC instants and `price` serialized with two decimals. Errors use the same text block with `isError: true`. Exposing `outputSchema`/`structuredContent` would require abandoning the accepted `ToolCallback` conversion for a raw `SyncToolSpecification` bean and is out of scope for Phase 1.

One adapter-level normalization exists: MCP `tools/call` makes `arguments` optional, but `McpToolUtils` forwards it verbatim and Spring AI's `MethodToolCallback` fails with `toolArguments must not be null` when it is absent. `mcp.tools.OptionalArgumentsToolCallback` maps an absent/blank/JSON-`null` payload to `{}` and forwards everything else unchanged. Tool definition, schema, argument binding and error handling remain the framework's.

### Slice 6 tool: `get_catalog_item`

`mcp.tools.GetCatalogItemTool` is the second adapter, constructed with `CatalogService` only. Its single required input `id` (integer) is passed unchanged to `CatalogService.getItem(Long)`, and the returned `CatalogItemView` is mapped with the same shared `SearchCatalogItem` record the search tool uses, so the item field set, price scale and ISO-8601 UTC timestamp formatting are identical across both tools. The adapter owns no lookup, validation or error translation, and never reaches the repository.

Input schema (generated): one property `id` of type `integer`, `required: ["id"]`, `additionalProperties: false`; the positive-identifier rule is deliberately left out of the schema for the same reason as the search bounds. Output: the single item object `{id,sku,name,type,description,price,active,createdAt,updatedAt}` as JSON text content, identical in shape to one element of `search_catalog`'s `items`.

Error behaviour is inherited, not invented: an unknown identifier raises `CatalogItemNotFoundException` (`Catalog item not found: <id>`), a non-positive identifier raises `InvalidCatalogCriteriaException` (`Catalog item ID must be positive`), and a missing or non-integer `id` is rejected by the SDK's schema validation before the handler runs. All three surface as `isError: true` with a single text block and no stack trace, secret or fabricated item. Detail lookup applies no active/type filter: inactive rows are retrievable by identifier, matching `CatalogService.getItem`.

`McpToolConfiguration` registers both annotated adapter objects in one `ToolCallbackProvider`, so the two tools share the accepted conversion and the callback decorators described below.

### Slice 7 MCP-boundary error sanitization

Spring AI converts any exception thrown by a tool callback into an MCP tool error whose text is `exception.getMessage()`. The application's own validation and not-found messages are intentional, but an unexpected failure (JDBC, connectivity, serialization) could otherwise carry SQL text, connection strings, file paths, credentials or stack frames to an MCP client, contrary to PRD §14.

`mcp.tools.SanitizingToolCallback` is the outermost callback decorator. It rethrows `InvalidCatalogCriteriaException` and `CatalogItemNotFoundException` unchanged — found anywhere in the cause chain, so framework wrapping does not hide them — and replaces every other `RuntimeException` with `SanitizedToolFailureException`, whose fixed message is `The tool failed due to an internal server error and returned no data.` The original failure is logged server-side at ERROR (tool name plus throwable; never the tool arguments, which may contain query data) and retained only as the cause. The decorator resolves the tool name defensively so that error reporting cannot itself fail.

This is an additional `ToolCallback` in the already-accepted chain, not a new registration mechanism: tool name, description, input schema, argument binding, result conversion and the `ToolCallbackProvider` conversion are untouched, and no application exception or documented tool error changed. Schema-validation failures for malformed arguments are raised by the MCP SDK before the handler runs and therefore keep the SDK's own (non-sensitive) wording.

### Origin and Host request protection

The MCP SDK provides `DefaultServerTransportSecurityValidator`, but Spring AI 2.0.1's auto-configuration builds the transport provider without one, leaving the SDK builder default of `ServerTransportSecurityValidator.NOOP`; the starter exposes no allowlist property. `mcp.config.McpServerConfiguration` therefore supplies the `WebMvcStreamableServerTransportProvider` bean itself, mirroring the framework construction (same `mcpServerJsonMapper`, endpoint, keep-alive and delete settings) plus the validator. The auto-configuration backs off on the same bean type, and its router-function bean still registers `/mcp` against the supplied provider. This implementation is the concrete "explicit allowlist" the decision gate required; it is not inherited from transport defaults.

Validation runs on GET, POST and DELETE before Accept-header or JSON-RPC handling:

- absent or blank `Origin` is allowed (non-browser MCP clients send no `Origin`);
- a present `Origin` must match `mcp.server.security.allowed-origins` exactly or by the SDK's `scheme://host:*` port wildcard, otherwise HTTP 403 `Invalid Origin header`;
- a present `Host` must match `mcp.server.security.allowed-hosts`, otherwise HTTP 421 `Invalid Host header`; an empty host list disables that check;
- an empty origin list rejects every request that sends an `Origin` (deny-by-default).

Defaults are loopback-only (`http://127.0.0.1:*`, `http://localhost:*`, and the matching hosts) and are overridable with `MCP_SERVER_SECURITY_ALLOWED_ORIGINS` / `MCP_SERVER_SECURITY_ALLOWED_HOSTS`. This does not change exposure: Compose still publishes only `127.0.0.1:8080`, PostgreSQL remains unpublished, and host execution still defaults to `SERVER_ADDRESS=127.0.0.1`. Slice 8 resolved the open browser-origin question with MCP Inspector 2.7.0: the Inspector web UI drives a local proxy that performs the MCP HTTP requests, so no `Origin` header reaches the server from the browser, and the Inspector default endpoint (`http://localhost:6274`) falls inside the existing loopback wildcard in any case. No allowlist change was needed, and the default posture was re-verified after the observation.

Because the provider bean is defined by the application rather than the starter, a Spring AI upgrade must re-check the provider builder arguments and the starter's back-off conditions. That is the accepted cost of pinning to Spring AI 2.0.1 / MCP SDK 2.0.0.


## Slice 11 REST adapter (D1 and D2 approved)

`com.example.mcpcatalog.rest.CatalogController` delegates directly to CatalogService.
`GET /api/v1/catalog` binds optional `type`, `active`, `maxPrice`, `text`, `page`,
`pageSize` to the existing criteria; `GET /api/v1/catalog/{id}` delegates detail lookup,
including inactive records. No service, persistence, schema, migration or MCP contract
changes are required. The existing annotation-free CatalogPage/CatalogItemView records
are serialized as JSON: search has `items`, `page`, `pageSize`, `totalItems`, `totalPages`;
items retain all nine fields with camelCase timestamps. No MCP DTO is referenced.

CatalogService exclusively owns defaults, bounds, filtering, ordering and validation.
Spring MVC performs standard String/Boolean/BigDecimal/Integer/Long binding; malformed
values fail before invocation. The adapter adds no default values or validation bounds.
Successful calls return 200, service validation/malformed binding returns 400, missing
items return 404, and unexpected failures return a sanitized 500.
`CatalogRestExceptionHandler` is scoped to the REST package so MCP errors are unaffected.
The RestError JSON contains exactly `status`, `error` (HTTP reason phrase), `message`,
`path` (request URI without query parameters). Only existing safe application validation
and not-found messages are exposed; binding errors use `Malformed request parameter`
and unexpected failures use `Internal server error`, never exception diagnostics.

D2 replaces `noRestControllerOrRequestMappedEndpointExistsInPhaseOne` with
`restControllersAndRequestMappingsExistOnlyInTheRestAdapterPackage`, scanning all
production classes. Added checks prohibit REST persistence/SQL/MCP dependencies and
catalog application/persistence dependencies on REST/web/servlet types. Existing MCP
boundary tests and all other Phase 1 tests remain. Delegation tests additionally verify
that optional and invalid service inputs are passed unchanged. No CORS, authentication,
provider wiring, frontend, dependency upgrades or network configuration changes are added.


## Slice 12 frontend decision gate (D3 resolved)

User-approved standalone Angular 22.x frontend, signals for local UI state, npm with
package-lock.json and npm ci. Registry verification on 2026-09-23 resolves Angular
core/compiler 22.2.0 and stable CLI/build 22.1.8 (major 22, compatible peer ranges).
Both accept Node 22.22.3. Pin Node 22.22.3 and its bundled npm 10.9.8; no prereleases,
NgModules, alternative package managers, or Java dependency changes are required.
The production build image is node:22.22.3-bookworm-slim; nginx:1.30.5-alpine3.24
serves static assets with SPA fallback and proxies only /api/ to
http://mcp-catalog-server:8080. The existing backend service name is retained.

Browser -> same-origin /api/v1/catalog -> REST adapter -> CatalogService -> PostgreSQL.
Local Angular development binds 127.0.0.1:4200 and proxies /api to
http://127.0.0.1:8080. Container frontend publication is 127.0.0.1:4200 by default;
backend loopback publication and unpublished PostgreSQL remain unchanged. Angular
components/services use relative API URLs only. No CORS configuration, /mcp proxy,
MCP security change, secret or AI-provider setting is introduced.

Angular owns interaction/presentation only. It sends optional criteria and displays
server pagination metadata and safe errors. Service validation/defaulting remains
authoritative; no catalog filtering, price rounding or replicated business bounds.

Version evidence: npm registry metadata for @angular/core@22.2.0,
@angular/cli@22.1.8 and @angular/build@22.1.8; [Angular compatibility](https://angular.dev/reference/versions);
[official nginx image tags](https://github.com/docker-library/official-images/blob/master/library/nginx).


### Slice 12 implementation and validation

`frontend/src/app/catalog-api.ts` contains the accepted REST wire interfaces and a
relative-URL HttpClient search method. `App` is a standalone component with signals
for loading/results/errors. It maps form inputs without catalog validation, cancels
superseded requests and clears stale results on loading/failure. Blank controls are
omitted; price text retains its scale. Page navigation uses response metadata and
retains submitted filters, independent of unsubmitted edits. New searches omit page.
At the Slice 12 boundary no router, forms NgModule, detail route, custom domain engine or AI integration was added; Slice 13 adds routing/detail below.

The frontend Docker build uses npm ci and the production Angular build. The nginx
runtime listens on container port 8080, publishes only 127.0.0.1:4200 by default, and
has a static-page health check. Compose waits for the existing healthy backend before
starting frontend. `/api/` is proxied without rewriting the accepted URI; SPA fallback
serves index.html for client paths. `/mcp` and its subpaths explicitly return 404.
The frontend check confirms static serving; backend readiness separately confirms
PostgreSQL connectivity. No existing backend/network setting or dependency changed.

25 frontend tests cover mapping/UI state; the browser smoke script exercises the real
nginx and development proxy paths with the seeded PostgreSQL catalog. The full backend
regression gate retains 232 passing tests. Version lock also pins TypeScript 6.0.3,
RxJS 7.8.2, tslib 2.8.1, Vitest/browser provider 4.1.11, jsdom 28.1.0 and Playwright 1.63.0.
The matching explicit Vitest browser provider avoids an npm 10 optional-peer resolution
failure; no legacy-peer-deps, force flag or overrides are used.


## Slice 13 Angular detail

Angular Router 22.2.0 is the only added dependency and matches the approved core version;
all prior lockfile package versions remain unchanged. App is now the shared header/main/
footer shell. CatalogSearchComponent contains the existing search UI and its retained
16 tests. Routes are `/` for search and `/catalog/:id` for CatalogDetailComponent;
unknown frontend paths return to search. The existing nginx SPA fallback supports direct
URLs/refresh without any Docker or proxy changes.

CatalogApi.detail forwards the route ID as an encoded path segment to the existing
relative GET /api/v1/catalog/{id}; it does not parse large IDs into imprecise JavaScript
numbers or duplicate server validation. Detail shows all nine fields, formats timestamps
explicitly as UTC and displays price without inventing a currency. Signals represent
loading, item, not-found and error states. New route IDs cancel prior requests and clear
stale detail. Error handling distinguishes 404, safe 400, network/upstream unavailability
and generic failures; diagnostic bodies are not exposed. Retry and return navigation
remain available as appropriate; inactive items are never filtered out locally.

CatalogSearchState is a small in-memory navigation snapshot: last submitted filters,
last page request and draft form values. It retains no catalog records, performs no
validation and persists nothing to browser storage. Returning re-fetches the previous
page through REST and restores form edits separately from submitted filters. Full reloads
clear this snapshot and back-to-catalog then performs the normal default search. No
custom route-reuse strategy or general state-management framework was introduced.

Backend code, REST/MCP contracts, service semantics, migrations, Docker architecture,
nginx/dev proxy and security defaults remain unchanged. No Slice 14 work is included.

## Slice 14 local Ollama catalog assistant (D4–D7 and backend D9 approved)

**D4 — invocation path (approved):** the assistant uses **in-process Spring AI tool calling**,
not an internal MCP client. The existing `search_catalog` capability adapter
(`mcp.tools.SearchCatalogTool`) supplies the tool definition and ultimately delegates to
`CatalogService`, so service validation and bounds remain the only authority. No `/mcp`
self-connection, no new MCP tool and no MCP contract change. Tool execution is deliberately
resolved from the adapter object rather than published as a `ToolCallback` bean, because
MCP's `ToolCallbackConverterAutoConfiguration` collects every `ToolCallback` bean and would
otherwise register a second, un-sanitized `search_catalog`.

**D5 — workflow contract and limits (approved):** `POST /api/v1/catalog/assistant` is a new
endpoint; the accepted `/api/v1/catalog` search and detail endpoints are untouched. Request
`{"prompt": "..."}` (required, trimmed, non-blank, at most 1000 characters; 400 otherwise).
Response `{answer, capability, arguments, items, page, pageSize, totalItems, totalPages,
provider, model}` where `items` and the page metadata come from the capability result, not
from the model, and `arguments` are the arguments actually used. `answer` is the model's
summary of that result only. Chain-of-thought, prompts, stack traces, provider diagnostics
and secrets are never returned. Exactly one capability invocation per request: a custom
`ToolCallingManager` sets `maxTotalToolCalls(1)` with `onLimitExceeded(THROW)`. There is no
model or tool retry. Tool-execution failures for `InvalidCatalogCriteriaException` are
rethrown so model-generated arguments that violate service rules produce the same 400 and
message as the REST search endpoint. Unsupported/non-catalog intent (no capability
invocation) returns 400 "This endpoint only supports catalog search requests." Provider
unavailability is a sanitized 503, a bounded provider timeout (default 60 s, externalized)
a sanitized 504, and anything unexpected a sanitized 500.

**D6 — local model and data path (approved):** `qwen3-coder-next:latest` is the Slice 14
model: already installed, advertises tool capability, and demonstrated real native
`tool_calls` in Slice 9, whereas `qwen2.5-coder:14b` returned arguments as plain text.
Ollama stays outside Compose; the backend reaches the host through
`OLLAMA_BASE_URL` (`http://127.0.0.1:11434` on the host, `http://host.docker.internal:11434`
in Compose) with the minimal Linux `extra_hosts: host.docker.internal:host-gateway`
mapping. No Ollama Compose service and no change to backend/PostgreSQL host publication.
Local mode talks only to local Ollama and the existing local application/database path; no
hosted provider call or credential is involved.

**D7 — provider configuration and startup (approved):** `spring-ai-starter-model-ollama`
2.0.1 is added from the existing Spring AI BOM with no version upgrade. Application-level
`catalog.ai.enabled` / `catalog.ai.provider` / `catalog.ai.timeout` select the workflow,
while the starter's `spring.ai.ollama.*` properties configure the connection and model;
both are environment-backed (`AI_ENABLED`, `AI_PROVIDER`, `OLLAMA_BASE_URL`, `OLLAMA_MODEL`,
`AI_TIMEOUT`). The assistant service and chat client exist only when the assistant is
enabled **and** the provider is `ollama`, so REST, MCP and catalog behavior start and work
with Ollama absent and never contact it during startup (`spring.ai.ollama.init.pull-model-strategy:
never`); a disabled or unknown provider yields a safe 503 from the assistant endpoint only.
No hosted provider is implemented and no automatic fallback exists; Slice 15 owns
multi-provider selection.

This keeps provider orchestration outside catalog business logic, MCP transport and
persistence: the `ai` package depends on the capability adapter and `CatalogService` only,
and no MCP, REST or migration behavior changes.

Implementation notes. The tool is the existing `SearchCatalogTool` adapter, passed through
the non-deprecated `ChatClient … .tools(adapter)` API: Spring AI 2.0.1 deprecates every
`ToolCallback`-based ChatClient method for removal, so the single invocation is observed by
a recording `ToolCallingManager` rather than a `ToolCallback` decorator, and no
`ToolCallback` bean is published (MCP's converter would otherwise register a second,
un-sanitized `search_catalog`). Each request builds its own chat client so the one-call
bound and the recording stay request-scoped; supplying our own `ToolCallingAdvisor` makes
Spring AI skip its automatic one. `InvalidCatalogCriteriaException` is rethrown by the tool
exception processor rather than fed back to the model, so invalid generated arguments yield
the same 400 and message as REST search. `spring.ai.retry.max-attempts` is set to `0`:
Spring AI's default retries transient provider failures up to 10 times, which both violated
D5's no-retry rule and misreported an unreachable provider as a timeout. Live validation,
including the local data path and PostgreSQL-grounded D9 scenario, is recorded in
[Slice 14 validation](SLICE_14_VALIDATION.md).

## Slice 15 hosted provider selection (D7 finalized, D8/D9 hosted approved)

**D8 — hosted provider (approved):** the hosted provider is **Bailian / Alibaba Cloud
Model Studio (DashScope)** used through its **OpenAI-compatible API**. The added dependency
is the BOM-managed `spring-ai-starter-model-openai` with no explicit version and no upgrade
of Spring AI, Spring Boot or MCP. That module is the **OpenAI-compatible protocol client
only**: the logical, application-level provider is `bailian` and that is what configuration
and the assistant response carry. No `spring-ai-alibaba` or other third-party provider
framework is introduced. The hosted model is `qwen3.8-max`. The live probe of this model
returned HTTP 403 `AccessDenied.Unpurchased` ("Access to model denied") from the Bailian
account, so the required native tool call is not yet demonstrated and Slice 15 is not
accepted; no replacement model was chosen without approval.

Credentials are environment-only: `BAILIAN_API_KEY` is read from the process environment,
never committed, never written to `.env`, never returned in a response and never logged;
`.env.example` carries a placeholder only. The endpoint is `BAILIAN_BASE_URL`, defaulting to
the official documented DashScope OpenAI-compatible base URL for the credential's region
(`https://dashscope-intl.aliyuncs.com/compatible-mode/v1`) and overridden at run time for
actual validation with the account's workspace-specific endpoint; per the Model Studio documentation
the base URL ends with `/compatible-mode/v1` and excludes `/chat/completions`, and an API key
is bound to the region of the endpoint it calls. The model is `BAILIAN_MODEL` (default
`qwen-plus`).

**D7 finalized — provider selection and startup:** the default remains `AI_PROVIDER=ollama`
and the supported logical values are exactly `ollama` and `bailian`. Selection is
configuration/startup based only: no hot switching, no provider-selection UI and no
automatic fallback. Internally a single `EnvironmentPostProcessor`
(`AiProviderEnvironmentPostProcessor`, registered in `META-INF/spring.factories`) maps the
logical provider onto Spring AI's model switch — `ollama` → `spring.ai.model.chat=ollama`,
`bailian` → `spring.ai.model.chat=openai` — so exactly one ChatModel bean is ever created and
the two Spring AI auto-configurations never compete. `spring.ai.model.embedding` is set to
`none` because this application uses no embedding models and both starters' embedding
auto-configurations would otherwise be active at once. The chat auto-configurations stay
otherwise conventional, so the existing `spring.ai.ollama.*` and `spring.ai.openai.*`
properties are used underneath.

Startup behaviour is option 6b: the application starts normally when hosted credentials are
absent, when Bailian is unreachable and when Ollama is unavailable, and normal REST, MCP and
catalog functionality stays usable. Ollama mode requires no hosted credential. If
`AI_PROVIDER=bailian` but the credential or configuration is missing or unusable, only the
assistant endpoint returns a sanitized 503. This works because Spring AI's OpenAI setup
builds a no-auth client for an empty key rather than asserting at startup.

Provider metadata is neutral: the assistant no longer assumes the model name comes from
`spring.ai.ollama.chat.model`. Each provider configuration contributes the logical provider
and its own configured model, so the response reports `ollama / qwen3-coder-next:latest` or
`bailian / qwen3.8-max`. The accepted assistant response schema is unchanged, as is the single
workflow, the one-capability-call bound, grounding in the capability result, `AI_TIMEOUT`
(60 s default) and `spring.ai.retry.max-attempts=0` (no provider retry, no fallback).

The AI-adapter failure classifier is extended provider-neutrally so Spring AI's
`TransientAiException`/`NonTransientAiException` and underlying client failures also map to
the approved statuses: invalid generated catalog arguments stay 400, provider
unavailable/auth/quota/provider-side failure becomes a sanitized 503, timeouts a sanitized
504 and unexpected failures a sanitized 500. Raw provider bodies, credential details,
provider stack traces and internal exception class names are never returned, and no
`CatalogService`, REST catalog, MCP, persistence or workflow-semantics change is involved.

## Slice 16 Angular catalog assistant

The catalog search page includes one standalone CatalogAssistantComponent below the
conventional results. CatalogAssistantApi posts only `{prompt}` to relative
`/api/v1/catalog/assistant`. Existing nginx/development proxy routing is unchanged:
browser → REST assistant → CatalogAssistantService → local Ollama → existing
`search_catalog` capability → CatalogService → PostgreSQL. No browser provider calls,
provider configuration, credentials, MCP access or CORS changes are introduced.

The backend owns prompt validation, interpretation, catalog bounds and returned facts.
Angular renders the answer as escaped text and items in returned order, including
detail links and returned totals; it does not infer filters or fetch additional pages.
Blank/oversized prompts reach backend validation. Sanitized contract 400 messages are
displayed for correction; malformed 400 bodies and other failures use fixed safe text.
503/network failures, 504 timeouts and unexpected failures allow an explicit retry of
the last submitted prompt. There is no automatic retry or conversation history.
New submissions clear old results and cancel the previous browser subscription;
destruction cancels pending work. Browser cancellation does not guarantee cancellation
of already-running backend inference. Provider/model metadata is not rendered.

D9 local UI scenario is approved: “Show me active service items under $200.” with
`AI_PROVIDER=ollama`, `OLLAMA_MODEL=qwen3-coder-next:latest`, expected persisted IDs
13, 14, 15, 16, 19, 20. Hosted Slice 15 acceptance remains on hold; Slice 16 makes
no hosted acceptance claim. No backend, migration, contract, dependency or security
changes are required. See [Slice 16 validation](SLICE_16_VALIDATION.md).
