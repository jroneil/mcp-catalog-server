# MCP Catalog Platform architecture

Decision gate resolved 2026-09-23. Scope: implementation plan v0.1, decision gate and Slice 1 only. The PRD v0.3 governs product requirements; the reference guide's alternative slice numbering does not govern delivery.

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

Host execution defaults to a loopback listener. Compose explicitly sets the listener to `0.0.0.0` **inside the container** so port forwarding works; the host publication remains hardcoded to `127.0.0.1`. Changing the host port does not widen binding. Credentials are required environment configuration without committed defaults. Local `.env` files are ignored. SQL initialization is disabled; no schema, migrations, repositories or catalog code exist in this slice.

## Later boundaries and gates

MCP adapter -> CatalogService -> repository -> PostgreSQL. REST will reuse the same service in Phase 2. Blocking persistence is consistent with synchronous MCP and MVC.

Flyway is only dependency-managed now; Slice 2 adds it to runtime and owns all schema changes. MCP BOMs are only dependency management now; Slice 4 introduces the WebMVC starter and `/mcp`. Origin protection will be implemented and tested at that gate using an explicit allowlist/equivalent request filter, not assumed from transport defaults. Unsupported protection or transport incompatibility is a stop condition. No production authentication is implied.

PostgreSQL integration tests must use Testcontainers and fail if Docker is unavailable; no silent skipping. Slice 1 tests load the real context against an isolated PostgreSQL container and verify health and JDBC connectivity without creating schema. Later host validation needs a user-configured Claude Code installation/account and must keep the MCP server private.

The PRD's complete Phase 1 startup acceptance (schema, seed, MCP tools) remains deferred to its designated slices. `backend/` follows the implementation plan and explicit task, replacing the PRD's illustrative `server-java/` layout. No Slice 2 work is authorized here.
