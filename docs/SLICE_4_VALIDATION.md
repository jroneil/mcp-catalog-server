# Slice 4 validation

Executed 2026-09-23. **Slice 4 passed; Slice 5 has not been started.**

Slice 4 activates the MCP server infrastructure, the synchronous Streamable HTTP
transport at `/mcp`, request-origin protection, and the tool-registration/discovery
mechanism. It implements **no catalog tool behavior**: `search_catalog` (Slice 5)
and `get_catalog_item` (Slice 6) do not exist yet, and the running server registers
zero tools.

## Files created/changed

Modified:

- `backend/pom.xml`: activated the already-pinned Spring AI/MCP runtime by adding
  `org.springframework.ai:spring-ai-starter-mcp-server-webmvc` (version from the
  imported `spring-ai-bom`). The BOM/MCP-BOM pins themselves are unchanged.
- `backend/src/main/resources/application.yml`: added the `spring.ai.mcp.server`
  block (enabled, identity, version, protocol, type, stdio, capabilities,
  annotation scanner, Streamable HTTP endpoint) and the `mcp.server.security`
  Origin/Host allowlists.

Created under `backend/src/main/java/com/example/mcpcatalog/mcp/config/`:

- `package-info.java`: states the adapter boundary (no business logic, no repository access).
- `McpTransportSecurityProperties.java`: `mcp.server.security.allowed-origins` / `allowed-hosts`.
- `McpServerConfiguration.java`: native SDK Origin/Host validator plus the
  Origin-validating Streamable HTTP transport provider.

Created under `backend/src/test/java/com/example/mcpcatalog/mcp/`:

- `config/McpTransportSecurityTest.java`: 9 allowlist cases.
- `McpServerWiringTest.java`: 10 context/HTTP/registry cases.
- `McpToolDiscoveryTest.java`: 2 real-MCP-client discovery cases (test-scoped probe tool).

Documentation: created this file; updated `docs/ARCHITECTURE.md` and `README.md`.

Unchanged and re-verified byte-identical to the pre-Slice-4 baseline
(SHA-256 checked before and after): `V1__create_catalog_item.sql`,
`V2__seed_catalog_items.sql`, `docker-compose.yml`, `backend/Dockerfile`.
`CatalogService`, its DTOs, repository fragment, and all earlier tests were not touched.

## Dependencies activated

| Artifact | Version | Source |
| --- | --- | --- |
| `org.springframework.ai:spring-ai-starter-mcp-server-webmvc` | 2.0.1 | `spring-ai-bom` |
| `org.springframework.ai:spring-ai-autoconfigure-mcp-server-webmvc` | 2.0.1 | transitive |
| `org.springframework.ai:spring-ai-autoconfigure-mcp-server-common` | 2.0.1 | transitive |
| `org.springframework.ai:spring-ai-mcp` | 2.0.1 | transitive |
| `org.springframework.ai:spring-ai-mcp-annotations` | 2.0.1 | transitive |
| `io.modelcontextprotocol.sdk:mcp` / `mcp-core` / `mcp-json-jackson3` | 2.0.0 | transitive |
| `org.springframework.boot:spring-boot-starter-web` | 4.1.1 | transitive (deprecated alias of the `spring-boot-starter-webmvc` already present) |

No version pin was changed. Boot 4.1.1, Spring AI 2.0.1 and MCP SDK 2.0.0 are
compatible: the published starter POM targets Boot 4.1.1 and resolves `mcp-core`
2.0.0, matching the pins that were already recorded in the decision gate. No
conflict was found, so no stop condition applied.

## MCP endpoint and transport configuration

```text
Endpoint:  http://127.0.0.1:8080/mcp          (HTTP GET/POST/DELETE)
Transport: Streamable HTTP, synchronous (Spring MVC / servlet)
```

```yaml
spring.ai.mcp.server:
  enabled: true
  name: mcp-catalog-server
  version: 0.1.0
  protocol: STREAMABLE
  type: SYNC
  stdio: false
  capabilities: { tool: true, resource: false, prompt: false, completion: false }
  annotation-scanner.enabled: false
  streamable-http.mcp-endpoint: /mcp
```

- `protocol=STREAMABLE` + `type=SYNC` select the WebMVC Streamable HTTP transport.
  No legacy SSE and no STDIO transport bean is created (`stdio: false`).
- Identity is `mcp-catalog-server` / `0.1.0`, matching the application version and
  the PRD's "MCP server version begins at 0.1.0". No `instructions` string is set,
  because there are no tools to describe until Slice 5.
- Only the tool capability is advertised. Resources, prompts and completions are
  explicitly disabled because they are Phase 3 concerns; the initialization result
  contains only `logging` and `tools`.
- The `@McpTool` annotation scanner is disabled. This slice registers tools through
  Spring AI `ToolCallback` beans, and the scanner emits a misleading
  `No tool methods found in the provided tool objects: []` warning when it is left
  enabled with no annotated beans.

## Tool registration and discovery mechanics

The registration mechanism is Spring AI's existing conversion of `ToolCallback` /
`ToolCallbackProvider` beans into `McpServerFeatures.SyncToolSpecification`; the
`McpSyncServer` then exposes them through `tools/list`. No custom registrar was
added, because duplicating framework behavior would violate the "no excessive
abstraction" constraint.

- The **shipped** application registers no tool. `McpSyncServer.listTools()` is empty
  and `tools/list` returns `{"tools":[]}`.
- Discovery mechanics are proven in `McpToolDiscoveryTest` with a **test-scoped**
  `ToolCallback` bean (`slice4_probe`). A real MCP Java SDK client connects over
  Streamable HTTP, initializes, lists tools, and reads the tool's generated input
  schema. The probe exists only in the test context and is never packaged.
- `McpServerWiringTest.noProductionCatalogToolsAreRegisteredYet` guards the shipped
  registry against accidental tool leakage before Slice 5.

## Origin validation approach

The selected stack supports request-origin protection natively through
`io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator`,
but Spring AI 2.0.1's `McpServerStreamableHttpWebMvcAutoConfiguration` builds the
transport provider **without** a validator, and the SDK builder therefore defaults to
`ServerTransportSecurityValidator.NOOP`. There is no `allowed-origins` property.

`McpServerConfiguration` therefore supplies the `WebMvcStreamableServerTransportProvider`
bean itself, mirroring the framework's construction (same `mcpServerJsonMapper`,
`mcp-endpoint`, keep-alive and delete settings) and adding `.securityValidator(...)`.
The auto-configuration backs off through its `@ConditionalOnMissingBean`, and its
`webMvcStreamableServerRouterFunction` still registers `/mcp` using the supplied
provider. This is the "explicit allowlist/equivalent request filter" the decision gate
required, implemented with the SDK's own validator rather than transport defaults.

Behaviour (applied before Accept-header and JSON-RPC handling on GET, POST and DELETE):

| Situation | Result |
| --- | --- |
| No `Origin` header (non-browser MCP clients) | allowed |
| Blank `Origin` value | treated as absent, allowed |
| `Origin` matches allowlist exactly, or by `scheme://host:*` port wildcard | allowed |
| `Origin` present and not allowlisted | HTTP **403** `Invalid Origin header` |
| `Host` matches allowlist | allowed |
| `Host` present and not allowlisted | HTTP **421** `Invalid Host header` |
| `Host` missing while `allowed-hosts` is configured | HTTP **421** `Invalid Host header` |
| `allowed-hosts` empty | Host validation disabled |
| `allowed-origins` empty | every request that sends an `Origin` is rejected (deny-by-default) |

Defaults are loopback-only and are the only production values:

```yaml
mcp.server.security:
  allowed-origins: [ "http://127.0.0.1:*", "http://localhost:*" ]
  allowed-hosts:   [ "127.0.0.1:*",       "localhost:*" ]
```

Both are overridable with `MCP_SERVER_SECURITY_ALLOWED_ORIGINS` /
`MCP_SERVER_SECURITY_ALLOWED_HOSTS` (comma-separated). This satisfies PRD FR-4 and
Phase 1 security without broadening exposure. Loopback binding is unaffected: the
compose host publication remains hardcoded to `127.0.0.1`, and host execution still
defaults to `SERVER_ADDRESS=127.0.0.1`.

## Commands run

```bash
# Baseline before editing: 55 tests green, artifact hashes captured.
mvn -f backend/pom.xml --batch-mode --no-transfer-progress clean verify
sha256sum backend/src/main/resources/db/migration/*.sql docker-compose.yml backend/Dockerfile > /tmp/mcp-slice4-baseline.sha256

# Focused iteration on the new suites.
mvn -f backend/pom.xml --batch-mode --no-transfer-progress test \
  -Dtest='McpTransportSecurityTest,McpServerWiringTest,McpToolDiscoveryTest' -DfailIfNoSpecifiedTests=false

# Full gate after the final configuration change.
mvn -f backend/pom.xml --batch-mode --no-transfer-progress clean verify
sha256sum -c /tmp/mcp-slice4-baseline.sha256
mvn -f backend/pom.xml --batch-mode --no-transfer-progress dependency:tree \
  '-Dincludes=org.springframework.boot:*,org.springframework.ai:*,io.modelcontextprotocol.sdk:*,org.postgresql:*'

# Docker and runtime gates.
docker compose config --quiet
docker compose up --build --wait --wait-timeout 240
docker compose ps
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/readiness
docker compose port mcp-catalog-server 8080
docker inspect $(docker compose ps -q) --format '{{.Name}} health={{.State.Health.Status}} ports={{json .NetworkSettings.Ports}}'
docker compose logs --no-color mcp-catalog-server | rg 'Enable tools capabilities|Registered tools|WARN|ERROR|Started McpCatalog'
curl --silent -o /dev/null -w 'HTTP %{http_code}\n' http://127.0.0.1:8080/mcp
curl --silent -o /dev/null -w 'HTTP %{http_code}\n' -X POST http://127.0.0.1:8080/mcp \
  -H 'Origin: http://evil.example' -H 'Accept: application/json, text/event-stream' \
  -H 'Content-Type: application/json' -d 'not-json'
curl --silent -o /dev/null -w 'HTTP %{http_code}\n' -X POST http://127.0.0.1:8080/mcp \
  -H 'Host: evil.example' -H 'Accept: application/json, text/event-stream' \
  -H 'Content-Type: application/json' -d 'not-json'
# initialize + tools/list on a live session
curl --silent -D /tmp/mcp-init-headers.txt -X POST http://127.0.0.1:8080/mcp \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H 'Origin: http://127.0.0.1:8080' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl-slice4","version":"0.1.0"}}}'
curl --silent -X POST http://127.0.0.1:8080/mcp -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' -H 'Origin: http://127.0.0.1:8080' \
  -H "Mcp-Session-Id: <session-from-initialize>" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
SELECT version, description, checksum, success FROM public.flyway_schema_history ORDER BY installed_rank;
SELECT count(*) AS catalog_rows FROM public.catalog_item;
SQL
```

## Test/build results

`mvn clean verify`: **BUILD SUCCESS — 76 tests, 0 failures, 0 errors, 0 skips.**

| Suite | Cases | Result |
| --- | ---: | --- |
| CatalogServiceTest (Slice 3) | 25 | PASS |
| CatalogServicePostgresTest (Slice 3) | 23 | PASS |
| CatalogPersistenceTest (Slice 2) | 5 | PASS |
| FlywayStartupFailureTest (Slice 2) | 1 | PASS |
| McpCatalogApplicationTest (Slice 1) | 1 | PASS |
| **Pre-existing subtotal** | **55** | **PASS (all unchanged)** |
| McpTransportSecurityTest (Slice 4, new) | 9 | PASS |
| McpServerWiringTest (Slice 4, new) | 10 | PASS |
| McpToolDiscoveryTest (Slice 4, new) | 2 | PASS |
| **Total** | **76** | **PASS** |

The new suites verify: `McpSyncServer` identity `mcp-catalog-server`/`0.1.0`; only the
tool capability advertised (resources/prompts/completions null); the shipped registry
is empty; the Origin-validating provider replaces the SDK NOOP validator; `/mcp`
answers 400 (registered route) rather than 404; cross-origin POST → 403; loopback
`Origin` and absent `Origin` pass validation; non-loopback `Host` → 421 over a raw
socket (the JDK HTTP client cannot set a synthetic `Host`); and a real MCP SDK client
initializes over Streamable HTTP and discovers the test-scoped probe tool with its
input schema. All allowlist semantics are also asserted directly against the validator
produced by the adapter configuration.

## Docker/health results

- `docker compose config --quiet` and `docker compose up --build --wait` both passed; the
  backend image was rebuilt and both services are healthy.
- Aggregate health: HTTP 200 `{"groups":["liveness","readiness"],"status":"UP"}`.
  Readiness: HTTP 200 `{"status":"UP"}`.
- Publication: backend `8080/tcp` → HostIp `127.0.0.1`, HostPort `8080` only;
  PostgreSQL `5432/tcp` → `null` (still not exposed on the host).
- Startup log: `Enable tools capabilities, notification: true`, no `Registered tools`
  line, no WARN/ERROR, `Started McpCatalogApplication`.
- `/mcp`: GET → 400 (`Invalid Accept header. Expected TEXT_EVENT_STREAM`);
  POST with `Origin: http://evil.example` → 403 `Invalid Origin header`;
  POST with `Host: evil.example` → 421 `Invalid Host header`.
- Live MCP session over the loopback endpoint: `initialize` → HTTP 200 with
  `Mcp-Session-Id`, `serverInfo {"name":"mcp-catalog-server","version":"0.1.0"}` and
  capabilities containing only `logging` and `tools`; `tools/list` → HTTP 200 SSE
  `{"tools":[]}`.
- Flyway history unchanged (V1 checksum `162354501`, V2 checksum `-2124124441`,
  both `success=t`); the database still holds exactly 24 seed rows.

## Deviations, compatibility notes, risks

**Compatibility findings.** No version conflict. The framework's actual API differs
from the implementation plan's shorthand in two documented details: the transport is
selected by `spring.ai.mcp.server.protocol=STREAMABLE` / `type=SYNC` with the endpoint
at `spring.ai.mcp.server.streamable-http.mcp-endpoint` (`/mcp` by default), and Origin
validation is supported by the SDK but not auto-configured, so the adapter supplies the
transport bean. Neither changes the architecture; both are recorded in
`docs/ARCHITECTURE.md`.

**PRD/plan deviations.**

1. Restricting capabilities to `tool` only is a deliberate interpretation of the Phase 1
   exclusions (no MCP resources, prompts or completions). The framework would otherwise
   advertise them by default.
2. Disabling the `@McpTool` annotation scanner is an added configuration decision, not a
   PRD requirement; it prevents a misleading empty-registry warning and keeps the
   declared registration mechanism (`ToolCallback` beans) unambiguous.
3. `spring-boot-starter-web` is pulled transitively by the MCP starter. In Boot 4.1.1 it
   is the deprecated alias of `spring-boot-starter-webmvc`, which was already present, so
   no exclusion was added and the classpath is otherwise unchanged.

**Unresolved risks/questions.**

1. **Browser-origin clients.** Only loopback origins on any port are allowed by default.
   A browser-based tool that sends its own `Origin` (for example an MCP Inspector web UI
   served from `http://localhost:6274`) will receive 403 until that origin is added via
   `MCP_SERVER_SECURITY_ALLOWED_ORIGINS`. This must be confirmed and documented in the
   Slice 8 Inspector validation; it has not been tested here.
2. **Host allowlist vs. container hostnames.** Requests that reach `/mcp` with a
   non-loopback `Host` (for example `mcp-catalog-server:8080` from another container) are
   rejected with 421. That is intended for the loopback-only Phase 1 posture, but a future
   container-to-container client would need an allowlist entry.
3. **Transport-bean override tracks the framework.** `McpServerConfiguration` mirrors the
   starter's provider construction. A Spring AI upgrade must re-check that the builder
   arguments and `@ConditionalOnMissingBean` back-off still match. This is the deliberate
   cost of the pinned-version decision.
4. `keepAliveInterval`/`disallowDelete` remain at framework defaults; session limits
   (`maxSessions`, `sessionIdleTimeout`) are not exposed through configuration in 2.0.1 and
   were left untouched.

No rollback was needed. No schema change, dependency upgrade, catalog service change,
REST controller, frontend, Ollama/hosted-AI integration, Python example, MCP resource,
prompt or STDIO transport was added. **Slice 5 remains unstarted.**
