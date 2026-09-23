# Slice 11 — Read-only Catalog REST Adapter

**Status:** Complete — 2026-09-23  
**Governing plan:** [Implementation plan v0.2](MCP_Catalog_Platform_IMPLEMENTATION_PLAN_v0.2.md), Slice 11  
**Phase 1 boundary:** [Slice 10 acceptance](SLICE_10_VALIDATION.md), 184 tests

## Objective and requirements implemented

Implement PRD FR-8/FR-9, AC-2–AC-5, REST error handling and REST tests, using the user-approved D1 contract. REST search and detail delegate to the existing CatalogService and PostgreSQL path. No catalog policy is duplicated or changed.

- `GET /api/v1/catalog`: optional `type`, `active`, `maxPrice`, `text`, `page`, `pageSize`; 200 JSON `items`, `page`, `pageSize`, `totalItems`, `totalPages`.
- `GET /api/v1/catalog/{id}`: 200 catalog item, including inactive items; 404 for missing positive IDs.
- Existing service defaults, bounds, filters, ordering and pagination apply unchanged.
- HTTP scalar binding rejects malformed values before service invocation. Existing validation failures return 400.
- Errors contain only `status`, `error`, `message`, `path`. Unexpected errors return 500 with `Internal server error`; binding failures use `Malformed request parameter`. Existing safe service validation/not-found messages are retained. Exception diagnostics are not serialized; path excludes query parameters.

## Files changed

Added:

- `backend/src/main/java/com/example/mcpcatalog/rest/CatalogController.java`
- `backend/src/main/java/com/example/mcpcatalog/rest/CatalogRestExceptionHandler.java`
- `backend/src/main/java/com/example/mcpcatalog/rest/RestError.java`
- `backend/src/test/java/com/example/mcpcatalog/rest/CatalogControllerTest.java`
- `backend/src/test/java/com/example/mcpcatalog/rest/CatalogRestIntegrationTest.java`
- `docs/SLICE_11_VALIDATION.md`

Updated:

- `backend/src/test/java/com/example/mcpcatalog/mcp/McpArchitectureBoundaryTest.java`
- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/TEST_PLAN.md`
- `docs/MCP_Catalog_Platform_IMPLEMENTATION_PLAN_v0.2.md` (created in the preceding planning task; D1 approval and Slice 11 status now recorded)

No changes to CatalogService/application records, persistence, migrations, MCP production code, dependency pins, Dockerfile, Compose, or application settings. Historical v0.1 and Slice 1–10 validation files remain byte-identical to their pre-planning checksums.

## Architecture decisions

The controller lives exclusively in `com.example.mcpcatalog.rest` and depends only on CatalogService/application models. Existing annotation-free CatalogPage/CatalogItemView records serialize directly, avoiding transport-specific types in the service and avoiding any MCP DTO dependency. REST exception advice is package-scoped so it does not alter MCP behavior.

D2 transition: the historical `noRestControllerOrRequestMappedEndpointExistsInPhaseOne` assertion was replaced by `restControllersAndRequestMappingsExistOnlyInTheRestAdapterPackage`. The full compiled-production-class scan remains; it now also covers composed mapping annotations and advice. Two added checks prohibit REST references to persistence/SQL/MCP and catalog application/persistence references to REST/web/servlet APIs. The original seven other architecture tests are retained. No test was disabled, skipped or moved out of scan scope. Controller tests verify actual delegation without defaults/validation duplication.

No new dependencies or version changes were needed.

## Tests added and results

| Suite / gate | Cases | Result |
| --- | ---: | --- |
| CatalogControllerTest | 9 | Passed: omitted/invalid criteria delegation, malformed scalar/ID binding before invocation, sanitized search/detail 500 responses |
| CatalogRestIntegrationTest | 37 | Passed against mandatory PostgreSQL 18.6 Testcontainers: each/combined filters, defaults, stable pages, maximum size, empty results, REST/MCP equality, active/inactive detail, malformed values, service validation and missing detail |
| McpArchitectureBoundaryTest | 10 | Passed; approved replacement plus two additional checks |
| Targeted gate | 56 | Zero failures/errors/skips |
| Full clean verify | 232 | BUILD SUCCESS; zero failures/errors/skips; packaged application |

The 184-test baseline is retained with the approved assertion replacement; 46 REST cases and two additional architecture tests bring the total to 232. Existing MCP schema, behavior, security, application, persistence and Flyway failure tests all passed. Tests require neither external AI nor manual database preparation.

Development failures were corrected before acceptance: the first test compilation used an incorrect JsonHelper import; the next run exposed an invalid test assumption about decimal scale across transports. REST retains `maxPrice=1.000` and the existing service rejects its scale. The accepted MCP JSON path normalizes trailing zeros before validation. Scale rejection is therefore tested independently for REST; equivalent representable criteria still compare full REST/MCP results and safe validation messages. No service or MCP behavior was changed to force wire identity.

## Exact validation commands

Targeted gate (rerun after the corrections above), then full gate:

```bash
mvn -f backend/pom.xml --batch-mode --no-transfer-progress test -Dtest=CatalogControllerTest,CatalogRestIntegrationTest,McpArchitectureBoundaryTest > /tmp/slice11-targeted.log 2>&1
mvn -f backend/pom.xml --batch-mode --no-transfer-progress clean verify > /tmp/slice11-full.log 2>&1
docker compose config --quiet
docker compose up --build --wait --wait-timeout 300 > /tmp/slice11-compose.log 2>&1
docker compose ps
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/readiness
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank; SELECT count(*) AS catalog_rows FROM catalog_item;"'
docker inspect --format '{{.Name}} health={{.State.Health.Status}} bindings={{json .HostConfig.PortBindings}}' mcp-catalog-server-postgres-1 mcp-catalog-server-mcp-catalog-server-1
docker compose logs --no-color mcp-catalog-server | rg 'Successfully validated|Schema .*up to date|Current version|Started McpCatalog'
git diff --check
sha256sum -c /tmp/mcp-planning-history.sha256
git diff --exit-code -- backend/src/main/java/com/example/mcpcatalog/catalog backend/src/main/java/com/example/mcpcatalog/mcp backend/src/main/resources backend/pom.xml backend/Dockerfile docker-compose.yml
```

The checksum manifest was captured in the preceding planning task and covers v0.1 and all Slice 1–10 validation records. Temporary logs are local evidence, not committed artifacts. Default shell sandbox startup failed before execution (`mountinfo path is not absolute`); authorized commands ran through the approved escalation mechanism.

Live HTTP verification command (standard-library check only; no Python implementation/example file added):

```bash
python3 - <<'PY'
import json, urllib.request, urllib.error
for path, expected in [('/api/v1/catalog',200),('/api/v1/catalog?type=SERVICE&active=true&maxPrice=200',200),('/api/v1/catalog/16',200),('/api/v1/catalog/22',200),('/api/v1/catalog/99999',404),('/api/v1/catalog?pageSize=101',400),('/api/v1/catalog?active=garbage',400)]:
    try:
        response = urllib.request.urlopen('http://127.0.0.1:8080'+path)
    except urllib.error.HTTPError as error:
        response = error
    body = json.load(response)
    assert response.status == expected, (path, response.status)
    if 'items' in body:
        print(path, response.status, {key: value for key,value in body.items() if key != 'items'}, 'ids=', [row['id'] for row in body['items']])
    else:
        print(path, response.status, body)
PY
```

## Docker, Flyway and live HTTP results

- Compose configuration and image build succeeded. `up --build --wait` completed with both services healthy. Docker's existing build stage skips tests; the separate full Maven gate above executed all tests.
- Health: `{"groups":["liveness","readiness"],"status":"UP"}`; readiness: `{"status":"UP"}`.
- Flyway validated two migrations; schema version 2 up to date. History versions 1 and 2 both successful; 24 catalog rows remain.
- Backend binding: `{"8080/tcp":[{"HostIp":"127.0.0.1","HostPort":"8080"}]}`. PostgreSQL bindings: `{}`.
- Default search: 200, IDs 1–20, page 0, pageSize 20, totalItems 24, totalPages 2.
- SERVICE + active + maximum 200: 200, IDs 13,14,15,16,19,20; totalItems 6.
- Detail 16: 200, SVC-104 / Network Health Assessment. Detail 22: 200, SVC-110 / active false.
- Detail 99999: 404, four-field safe error. pageSize 101: 400 with service validation message. active=garbage: 400 with generic malformed-parameter message.
- Unexpected 500 responses were verified by injected failures in MVC tests, not by breaking the running database.

Compose reused the existing database volume; clean V1/V2 application was independently exercised in fresh Testcontainers databases by the full gate. No volume deletion was required by Slice 11.

## Deviations, limitations and remaining decisions

No implementation scope deviation or unresolved Slice 11 gate. The existing decimal representation difference is documented above. Standard framework HTTP binding is used; no additional transport coercion policy is invented. D3–D9 remain future frontend/AI decisions and do not affect this slice. Existing compiler deprecation warnings in MCP tests remain unchanged.

Phase 1 remains complete and accepted. **No Slice 12 or later scope was added.** No Angular, AI-provider integration, catalog writes, CORS expansion, production authentication or Phase 3 features were introduced. Nothing was committed.
