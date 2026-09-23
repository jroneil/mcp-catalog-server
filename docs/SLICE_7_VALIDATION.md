# Slice 7 validation

Executed 2026-09-23. **Slice 7 passed; Slice 8 has not been started.**

Slice 7 proves the complete Phase 1 MCP request path end to end and adds one narrowly
scoped hardening measure at the MCP boundary. No tool contract, no `CatalogService`
behavior, no migration, no network exposure and no registration mechanism changed.

```text
MCP client -> Streamable HTTP /mcp -> transport security -> tool discovery
           -> search_catalog / get_catalog_item -> CatalogService -> repository -> PostgreSQL
```

## Files created/changed

Created (production, 2 files):

- `backend/src/main/java/com/example/mcpcatalog/mcp/tools/SanitizingToolCallback.java`:
  MCP-boundary decorator that preserves intended application errors and replaces
  unexpected failures with a fixed public-safe message.
- `backend/src/main/java/com/example/mcpcatalog/mcp/tools/SanitizedToolFailureException.java`:
  carries that message; the original failure is retained only as the cause.

Modified (production, 1 file):

- `backend/src/main/java/com/example/mcpcatalog/mcp/config/McpToolConfiguration.java`:
  one additional `.map(SanitizingToolCallback::new)` in the existing callback chain.

Created (tests, 4 files):

- `backend/src/test/java/com/example/mcpcatalog/mcp/tools/SanitizingToolCallbackTest.java` (10 cases).
- `backend/src/test/java/com/example/mcpcatalog/mcp/McpInternalFailureSanitizationTest.java` (7 cases).
- `backend/src/test/java/com/example/mcpcatalog/mcp/McpArchitectureBoundaryTest.java` (8 cases).
- `backend/src/test/java/com/example/mcpcatalog/mcp/PhaseOneEndToEndMcpTest.java` (23 cases).

Documentation: created this file; updated `docs/ARCHITECTURE.md` and `README.md`.

**No pre-existing test file was modified.** All 136 Slice 1–6 tests ran unmodified and
green. Every pinned artifact — V1/V2, `docker-compose.yml`, `Dockerfile`, `pom.xml`,
`application.yml`, all `catalog.application` and `catalog.persistence` sources,
`McpServerConfiguration`, `McpTransportSecurityProperties`, `SearchCatalogTool`,
`SearchCatalogResult`, `SearchCatalogItem`, `OptionalArgumentsToolCallback` and the
Slice 5/6 unit test files — is SHA-256 identical to the Slice 7 baseline.

## 2. Production code change

A production change **was** necessary for the error-sanitization item only; the
end-to-end verification required none. The defect is real: Spring AI turns any thrown
exception into an MCP tool error whose text is `exception.getMessage()`, so a JDBC or
connectivity failure could have carried SQL text, a connection string, a file path or a
credential-bearing message to the MCP client, contrary to PRD §14.

The fix is deliberately narrow and reuses the accepted mechanism:

- It is an additional `ToolCallback` decorator in the already-accepted chain
  (`MethodToolCallback` → `OptionalArgumentsToolCallback` → `SanitizingToolCallback`),
  registered through the same single `ToolCallbackProvider` bean. No custom registrar, no
  manual `SyncToolSpecification`, no change to how tools are discovered.
- No application exception changed. `InvalidCatalogCriteriaException` and
  `CatalogItemNotFoundException` are found anywhere in the cause chain and rethrown
  unchanged, so every previously documented error keeps its exact message.
- Tool names, descriptions, input schemas and success output shapes are untouched.

Behaviour added for previously-undefined failures:

| Failure | Before | After |
| --- | --- | --- |
| Unexpected internal error (e.g. database/connectivity) | `exception.getMessage()` echoed to the MCP client | fixed `The tool failed due to an internal server error and returned no data.` |

The original failure is logged server-side at ERROR (tool name plus throwable, never the
tool arguments, which may contain query data) so operators keep the diagnostics that the
caller no longer sees.

## 3. End-to-end scenarios covered

| Required scenario | Test |
| --- | --- |
| initialize succeeds | `PhaseOneEndToEndMcpTest.initializeSucceedsAndAdvertisesOnlyTheToolCapability` |
| tools/list returns exactly two production tools | `...toolsListReturnsExactlyTheTwoAcceptedProductionTools` |
| names exactly `search_catalog`, `get_catalog_item` | same test (`containsExactlyInAnyOrder`) |
| schemas unchanged from Slice 5/6 | `...inputSchemasAreUnchangedFromTheAcceptedSliceFiveAndSixContracts` (exact map equality against the frozen schema documents) |
| search: no arguments | `...noArgumentsReturnsTheDefaultFirstPageInIdAscendingOrder` |
| search: PRODUCT / SERVICE | `...productAndServiceTypeFiltersReturnOnlyTheirOwnType` |
| search: active=true / active=false | `...activeFilterSelectsActiveAndInactiveItems` |
| search: maxPrice | `...maximumPriceFilterIsInclusive` |
| search: case-insensitive text | `...textSearchIsCaseInsensitiveAndLiteral` |
| search: combined filters | `...combinedFiltersReturnOnlyMatchingRows` |
| search: page 0 / default size | `...noArgumentsReturnsTheDefaultFirstPageInIdAscendingOrder` |
| search: pageSize=100 | `...maximumPageSizeIsAcceptedAndOutOfRangePageKeepsTotals` |
| search: invalid pageSize / page / price / type / oversized text | `...invalidCriteriaReturnTheIntendedValidationMessages` |
| search: deterministic id ASC ordering | `...orderingIsDeterministicAcrossIdenticalCalls` |
| search: no unbounded result path | `...noUnboundedResultSetCanBeRequested` |
| search: seeded PostgreSQL rows match | `...resultsMatchTheSeededPostgresRowsExactly` |
| detail: known active item | `...knownActiveItemIsReturnedExactlyAsStored` |
| detail: known inactive item | `...knownInactiveItemIsRetrievableByIdentifier` |
| detail: unknown ID | `...unknownIdentifierReturnsTheDefinedNotFoundError` |
| detail: zero / negative ID | `...nonPositiveIdentifiersAreRejectedWithTheIntendedMessage` |
| detail: missing / wrong-typed ID | `...missingAndWrongTypedIdentifiersAreRejectedWithoutInternalDetails` |
| detail equals search representation for the same row | `...detailResultMatchesTheSearchRepresentationForTheSameRow` |
| valid loopback Origin / absent Origin accepted | `...validAndAbsentOriginsAreAcceptedButInvalidOriginsAndHostsAreRejected` |
| invalid Origin rejected (403) / invalid Host rejected (421) | same test (raw socket for the `Host` case) |
| `/mcp` remains Streamable HTTP | `...mcpEndpointRemainsRegisteredAsStreamableHttpOnly` |
| no STDIO / legacy SSE | same test (environment properties plus absence of transport beans) |
| only tools capability enabled | `...initializeSucceedsAndAdvertisesOnlyTheToolCapability` |
| backend bound to loopback | `...serverRemainsConfiguredForLoopbackOnly` plus the Docker gate |
| PostgreSQL unpublished | Docker gate (not meaningful inside Testcontainers) |
| unexpected internal failure sanitized | `McpInternalFailureSanitizationTest` (7 cases) |
| intended messages preserved | `McpInternalFailureSanitizationTest` plus every validation/not-found test |
| architecture boundaries | `McpArchitectureBoundaryTest` (8 cases) |

The Slice 5 and Slice 6 suites remain in place and re-run unchanged, so the matrix is
covered both by the dedicated slice suites and by this consolidated acceptance suite.

## 4. Error-sanitization result

Sanitization **was** added, narrowly, at the MCP boundary. It does not redesign the
exception architecture, does not change service exceptions and does not change any tool
contract.

How the raw-message risk is tested:

1. **Unit, deterministic.** `SanitizingToolCallbackTest` drives the decorator with
   synthetic failure text containing `SELECT ... public.catalog_item`, a JDBC URL with
   `user=`/`password=`, a Java file path and a fabricated stack frame. It asserts the
   thrown message equals the fixed sanitized text and contains none of those fragments;
   that a `DataAccessResourceFailureException` wrapper is sanitized too; that the original
   failure is retained as the cause; that the intended `InvalidCatalogCriteriaException`
   and `CatalogItemNotFoundException` messages pass through unchanged; and that the log
   path cannot itself fail (see the defect below).
2. **Integration, real path.** `McpInternalFailureSanitizationTest` injects a synthetic
   unexpected failure at the `CatalogService` boundary with a Mockito spy and drives it
   through the real MCP server with a real client over Streamable HTTP. It asserts the
   client receives exactly the fixed message, that the injected SQL/URL/credential/path
   text appears nowhere in the result, and that invalid search criteria, invalid IDs and
   not-found still return their intended service messages. Successful calls are asserted
   to be unaffected.

**Integration defect found and fixed.** The first version of the decorator built its log
message from `delegate.getToolDefinition().name()` inside the catch block. A delegate that
cannot supply its definition caused a secondary `NullPointerException` that replaced the
sanitized error. Error reporting must not itself fail, so the tool name is now resolved
defensively (`unknown` on failure); a dedicated test covers that case.

**Remaining, documented limitation.** Schema-validation failures for missing or
wrong-typed `id` are produced by the MCP SDK *before* the handler runs, so they do not
pass through the sanitizer. They contain no SQL, secret, path or stack frame (verified in
Slice 6 and re-asserted here), but their wording is owned by the SDK and can change on
upgrade.

## 5. Architecture-boundary verification

`McpArchitectureBoundaryTest` reads the compiled production classes and searches their
constant pools, so a reference in a method body is caught, not only one in a signature.

| Boundary | Assertion | Result |
| --- | --- | --- |
| MCP adapters must not touch persistence | no class under `mcp/` references `catalog/persistence` | PASS |
| MCP adapters must not own query mechanics | no class under `mcp/` references `java/sql`, `javax/sql` or `org/springframework/jdbc` | PASS |
| MCP reaches the catalog through the application layer | `mcp/tools` classes reference `catalog/application` | PASS |
| No MCP type leaks into the service layer | no class under `catalog/application` references `mcpcatalog.mcp`, `io/modelcontextprotocol`, `springframework/ai/mcp`, `springframework/web` or `jakarta/servlet` | PASS |
| No MCP type leaks into persistence | no class under `catalog/persistence` references `mcpcatalog.mcp` | PASS |
| No business logic in MCP configuration | no class under `mcp/config` references `CatalogService`, `CatalogSearchCriteria`, `catalog/persistence` or `java/sql` | PASS |
| No REST adapter exists in Phase 1 | no production class references `@RestController`, `@RequestMapping` or `@Controller` | PASS |
| Only Streamable HTTP is wired | no production class references the STDIO or SSE transport providers | PASS |

Adapter dependency direction is also asserted reflectively in the Slice 5/6 unit tests
(`SearchCatalogTool` and `GetCatalogItemTool` depend on `CatalogService` only).

## 6. Commands run

```bash
# Baseline before editing: 136 tests green, pinned-artifact hashes captured.
mvn -f backend/pom.xml --batch-mode --no-transfer-progress clean verify
sha256sum <migrations, compose, Dockerfile, pom, application.yml, catalog + mcp sources, Slice 5/6 unit tests> \
  > /tmp/mcp-slice7-baseline.sha256

# Focused iteration.
mvn -f backend/pom.xml --batch-mode --no-transfer-progress test -Dtest='SanitizingToolCallbackTest' -DfailIfNoSpecifiedTests=false
mvn -f backend/pom.xml --batch-mode --no-transfer-progress test -Dtest='McpInternalFailureSanitizationTest' -DfailIfNoSpecifiedTests=false
mvn -f backend/pom.xml --batch-mode --no-transfer-progress test -Dtest='McpArchitectureBoundaryTest' -DfailIfNoSpecifiedTests=false
mvn -f backend/pom.xml --batch-mode --no-transfer-progress test -Dtest='PhaseOneEndToEndMcpTest' -DfailIfNoSpecifiedTests=false

# Full gate and unchanged-artifact proof.
mvn -f backend/pom.xml --batch-mode --no-transfer-progress clean verify
sha256sum -c /tmp/mcp-slice7-baseline.sha256

# Docker, health, security and data-integrity gates.
docker compose up --build --wait --wait-timeout 240
docker compose ps
docker compose logs --no-color mcp-catalog-server | rg 'Registered tools|WARN|ERROR|Started McpCatalog'
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/readiness
docker compose port mcp-catalog-server 8080
docker inspect $(docker compose ps -q) --format '{{.Name}} health={{.State.Health.Status}} ports={{json .NetworkSettings.Ports}}'
curl --silent -o /dev/null -w 'HTTP %{http_code}\n' http://127.0.0.1:8080/mcp
curl --silent -o /dev/null -w 'HTTP %{http_code}\n' -X POST http://127.0.0.1:8080/mcp -H 'Origin: http://127.0.0.1:8080' -H 'Accept: application/json, text/event-stream' -H 'Content-Type: application/json' -d 'not-json'   # 400
curl --silent -o /dev/null -w 'HTTP %{http_code}\n' -X POST http://127.0.0.1:8080/mcp -H 'Accept: application/json, text/event-stream' -H 'Content-Type: application/json' -d 'not-json'                              # 400 (absent Origin)
curl --silent -o /dev/null -w 'HTTP %{http_code}\n' -X POST http://127.0.0.1:8080/mcp -H 'Origin: http://evil.example' -H 'Accept: application/json, text/event-stream' -H 'Content-Type: application/json' -d 'not-json'  # 403
curl --silent -o /dev/null -w 'HTTP %{http_code}\n' -X POST http://127.0.0.1:8080/mcp -H 'Host: evil.example' -H 'Accept: application/json, text/event-stream' -H 'Content-Type: application/json' -d 'not-json'            # 421
# live initialize -> tools/list -> search_catalog success + validation failure -> get_catalog_item success + not-found
docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
SELECT version, description, checksum, success FROM public.flyway_schema_history ORDER BY installed_rank;
SELECT count(*) AS catalog_rows FROM public.catalog_item;
SELECT type, active, count(*) FROM public.catalog_item GROUP BY type, active ORDER BY type, active;
SQL
```

## 7. Test/build results

`mvn clean verify`: **BUILD SUCCESS — 184 tests, 0 failures, 0 errors, 0 skips.**

| Suite | Cases | Result |
| --- | ---: | --- |
| CatalogServiceTest (Slice 3) | 25 | PASS |
| CatalogServicePostgresTest (Slice 3) | 23 | PASS |
| CatalogPersistenceTest (Slice 2) | 5 | PASS |
| FlywayStartupFailureTest (Slice 2) | 1 | PASS |
| McpCatalogApplicationTest (Slice 1) | 1 | PASS |
| McpTransportSecurityTest (Slice 4) | 9 | PASS |
| McpServerWiringTest (Slice 4) | 10 | PASS |
| McpToolDiscoveryTest (Slice 4) | 2 | PASS |
| SearchCatalogToolTest (Slice 5) | 13 | PASS |
| OptionalArgumentsToolCallbackTest (Slice 5) | 3 | PASS |
| SearchCatalogMcpIntegrationTest (Slice 5) | 20 | PASS |
| GetCatalogItemToolTest (Slice 6) | 12 | PASS |
| GetCatalogItemMcpIntegrationTest (Slice 6) | 12 | PASS |
| **Pre-Slice-7 subtotal** | **136** | **PASS (all unmodified)** |
| SanitizingToolCallbackTest (Slice 7, new) | 10 | PASS |
| McpInternalFailureSanitizationTest (Slice 7, new) | 7 | PASS |
| McpArchitectureBoundaryTest (Slice 7, new) | 8 | PASS |
| PhaseOneEndToEndMcpTest (Slice 7, new) | 23 | PASS |
| **Total** | **184** | **PASS** |

## 8. Live MCP results

Against the Dockerized server on `http://127.0.0.1:8080/mcp`:

- `initialize` → succeeds; `tools/list` → **exactly two tools**:
  `get_catalog_item`, `search_catalog`.
- `search_catalog` `{type:SERVICE, active:true, maxPrice:200, page:0, pageSize:20}` →
  `isError:false`, `totalItems:6`, `totalPages:1`, ids `[13,14,15,16,19,20]`.
- `search_catalog` `{pageSize:101}` → `isError:true`,
  `Page size must be between 1 and 100`.
- `get_catalog_item` `{id:16}` → `isError:false`, exact seeded row
  (`SVC-104`, `199.00`, `2026-01-15T09:00:00Z`).
- `get_catalog_item` `{id:99999}` → `isError:true`, `Catalog item not found: 99999`.

The sanitized generic message is exercised automatically (not over the live container,
which has no fault injection): `McpInternalFailureSanitizationTest` proves the production
wiring returns it for unexpected failures.

## 9. Docker/health/security results

- `docker compose up --build --wait` passed; both services healthy; startup log shows
  `Registered tools: 2` with no WARN/ERROR.
- Aggregate health HTTP 200 `{"groups":["liveness","readiness"],"status":"UP"}`;
  readiness HTTP 200 `{"status":"UP"}`.
- Backend publication remains `127.0.0.1:8080` only; PostgreSQL `5432/tcp` remains
  `null` (unpublished).
- Origin/Host behaviour unchanged: GET `/mcp` → 400; loopback `Origin` → 400 (accepted,
  body rejected); absent `Origin` → 400; `Origin: http://evil.example` → 403
  `Invalid Origin header`; `Host: evil.example` → 421 `Invalid Host header`.

## 10. Flyway/data integrity results

- `flyway_schema_history`: V1 `create catalog item` checksum `162354501`, V2
  `seed catalog items` checksum `-2124124441`, both `success=t`. No new migration.
- `catalog_item` row count 24; distribution `PRODUCT false 3`, `PRODUCT true 9`,
  `SERVICE false 3`, `SERVICE true 9` — identical to the Slice 2 seed baseline.

## 11. Deviations from PRD/plan

- **Error sanitization (new, narrow).** Slice 5/6 recorded raw exception messages as a
  known risk; Slice 7 fixes it at the MCP boundary per PRD §14. This is a hardening
  addition rather than a deviation: no service exception, tool contract, registration
  mechanism or transport setting changed, and all documented error messages are
  preserved exactly.
- No other deviation. The end-to-end suite intentionally restates scenarios already
  covered by the Slice 4–6 suites, because Slice 7's charter is an acceptance proof of
  the whole path rather than new behavior.
- Test-authoring note: YAML list properties cannot be read with
  `Environment.getProperty`; the e2e suite asserts the bound
  `McpTransportSecurityProperties` object instead, which is also a stronger check.

## 12. Unresolved risks/questions

1. **SDK-owned schema-validation wording** for missing/wrong-typed arguments remains
   outside the sanitizer and could change on an MCP SDK upgrade. It exposes no internal
   detail today.
2. **Server-side logs now contain the original internal failure** (by design, to keep
   diagnostics). Phase 1 does not add log redaction; logs are local-only, and tool
   arguments are deliberately never logged.
3. **`outputSchema`/`structuredContent`** remain unavailable through the accepted
   `ToolCallback` conversion (inherited from Slice 5).
4. **Transport/origin risks inherited from Slice 4** (browser-origin allowlist for a
   future Inspector UI, container-hostname `Host` rejection, transport-bean override
   tracking the framework).
5. Slice 8 (MCP Inspector validation) and Slice 9 (real local MCP host validation) are
   still outstanding; no Inspector or real-host claim is made here.

No rollback was needed: no tool contract changed, `CatalogService` remains the only
catalog entry point, the `ToolCallback` registration path is intact, network exposure is
unchanged, and no Phase 2/3 functionality was added. **Slice 8 remains unstarted.**
