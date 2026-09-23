# MCP Catalog Platform architecture

Decision gate resolved 2026-09-23. Current scope: implementation plan v0.1, decision gate and Slices 1–3. The PRD v0.3 governs product requirements; the reference guide's alternative slice numbering does not govern delivery.

## Pinned decisions

| Component | Decision |
| --- | --- |
| Java | Java 21, compiled with release 21 |
| Spring Boot | 4.1.1 (parent, starters, plugins and managed dependency baseline) |
| Spring AI | 2.0.1 BOM; MCP starter deferred to Slice 4 |
| MCP Java SDK | 2.0.0, matching Spring AI 2.0.1's published dependency; MCP BOM pinned, no runtime MCP dependency yet |
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
| Future MCP endpoint | `http://127.0.0.1:8080/mcp` |
| Future transport | Spring MVC, synchronous server, stateful Streamable HTTP (`protocol=STREAMABLE`, `type=SYNC`); no legacy SSE transport or STDIO |
| Later real-host validation | Claude Code on the same host, using its HTTP MCP connection support |

## Compatibility evidence

Verified against published releases, not tutorial versions:

- [Spring AI compatibility](https://docs.spring.io/spring-ai/reference/getting-started.html): 2.0.x supports Boot 4.0.x/4.1.x.
- [Spring AI 2.0.1 source POM](https://github.com/spring-projects/spring-ai/blob/v2.0.1/pom.xml): Boot 4.1.1.
- [Published Spring AI MCP 2.0.1 POM](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-mcp/2.0.1/spring-ai-mcp-2.0.1.pom): SDK 2.0.0. The reference's SDK 2.0.1 example is not imposed over the framework's tested dependency.
- [Boot 4.1.1 dependency BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom): Flyway, JDBC and Testcontainers versions above.
- [Spring AI MCP starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html): WebMVC Streamable HTTP and synchronous operation supported.
- [Claude Code HTTP MCP configuration](https://code.claude.com/docs/en/mcp): later use `claude mcp add --transport http catalog http://127.0.0.1:8080/mcp`. This is a future validation choice, not a completed interoperability test or backend AI-provider integration.
- Official image catalogs: [Java](https://github.com/docker-library/official-images/blob/master/library/eclipse-temurin), [Maven](https://github.com/docker-library/official-images/blob/master/library/maven), [PostgreSQL](https://github.com/docker-library/official-images/blob/master/library/postgres).

Exact release tags are intentional; image tags can receive upstream OS rebuilds. Dependency/image upgrades require review. No milestone, snapshot, or latest tags are used.

## Slice 1 runtime

```text
Host 127.0.0.1:8080 -> Spring MVC / Actuator -> JDBC DataSource -> PostgreSQL:5432
```

Only health is exposed through Actuator. Health/readiness includes database connectivity and hides details. PostgreSQL has no published host port. Compose waits for `pg_isready` over TCP before starting the backend; the backend health check independently verifies a real JDBC connection through Actuator. A named volume persists PostgreSQL data at `/var/lib/postgresql` (PostgreSQL 18 image layout).

Host execution defaults to a loopback listener. Compose explicitly sets the listener to `0.0.0.0` **inside the container** so port forwarding works; the host publication remains hardcoded to `127.0.0.1`. Changing the host port does not widen binding. Credentials are required environment configuration without committed defaults. Local `.env` files are ignored. Spring SQL script initialization remains disabled; Flyway now exclusively creates and seeds the catalog table as described below.

## Later boundaries and gates

MCP adapter -> CatalogService -> repository -> PostgreSQL. REST will reuse the same service in Phase 2. Blocking persistence is consistent with synchronous MCP and MVC.

Flyway 12.4.0 is active in Slice 2 and owns all schema changes. MCP BOMs are only dependency management now; Slice 4 introduces the WebMVC starter and `/mcp`. Origin protection will be implemented and tested at that gate using an explicit allowlist/equivalent request filter, not assumed from transport defaults. Unsupported protection or transport incompatibility is a stop condition. No production authentication is implied.

PostgreSQL integration tests must use Testcontainers and fail if Docker is unavailable; no silent skipping. The existing context test now also runs Flyway against its isolated PostgreSQL container before verifying health and JDBC connectivity. Later host validation needs a user-configured Claude Code installation/account and must keep the MCP server private.

The PRD's complete Phase 1 startup acceptance (schema, seed, MCP tools) remains deferred to its designated slices. `backend/` follows the implementation plan and explicit task, replacing the PRD's illustrative `server-java/` layout. Slice 3 is implemented; Slice 4 has not been started.

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

V1/V2, seed data, dependency pins, Dockerfile, Compose networking and health configuration remain unchanged. Service unit tests verify policy without Spring/SQL; PostgreSQL/Testcontainers service tests verify real query behavior and bounded pagination, including more than 100 matches using rolled-back test fixtures. Slice 4 remains unstarted.
