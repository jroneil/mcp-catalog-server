# Slice 14 validation — Local Ollama catalog workflow (backend)

Executed 2026-09-24. **Slice 14 passes.** Slice 15 has not been started.

Objective: enable a locally configured model to interpret a bounded natural-language
catalog request, invoke the existing catalog capability, and return results grounded in
the existing `CatalogService`/PostgreSQL data. Addresses FR-10, FR-12, the backend portion
of FR-13, PRD §9 and AC-8.

## 1. Approved decisions

D4–D7 and the backend portion of D9 were approved before implementation and recorded in
[ARCHITECTURE](ARCHITECTURE.md#slice-14-local-ollama-catalog-assistant-d4d7-and-backend-d9-approved):
in-process Spring AI tool calling (no internal MCP client, no new MCP tool); a new
`POST /api/v1/catalog/assistant` endpoint with the approved request/response contract,
one capability call per request, no retry, bounded timeout, and 400/503/504/500 error
mapping; `qwen3-coder-next:latest` as the local model with Ollama outside Compose and the
minimal `host.docker.internal:host-gateway` mapping; `spring-ai-starter-model-ollama`
2.0.1 from the existing BOM with application-level `catalog.ai.*` selection.

## 2. Implementation

| Component | Purpose |
| --- | --- |
| `ai/config/CatalogAssistantProperties` | `catalog.ai.enabled/provider/timeout` + the 1000-character prompt bound |
| `ai/config/CatalogAssistantConfiguration` | Creates the assistant service only when enabled **and** provider is `ollama` |
| `ai/CatalogAssistantService` | Bounded workflow: validate prompt, one capability call, grounded result, timeout bound, failure classification |
| `ai/CatalogCapabilityRecorder` | Per-request record of the arguments actually used and the capability result; enforces the one-call bound |
| `ai/RecordingToolCallingManager` | Delegates real tool execution to Spring AI while recording that one invocation |
| `ai/CatalogAssistantResult` | Response record (`answer`, `capability`, `arguments`, `items`, page metadata, `provider`, `model`) |
| `ai/*Exception` | `Invalid…`/`Unsupported…` (400), `Unavailable` (503), `Timeout` (504), `Internal` (500) |
| `rest/CatalogAssistantController` | HTTP adapter; no orchestration |
| `rest/CatalogRestExceptionHandler` | Added the assistant and malformed-body mappings to the scoped advice |

Design notes:

- The tool is the **existing `SearchCatalogTool` adapter**, passed with the non-deprecated
  `ChatClient … .tools(adapter)` API. Spring AI 2.0.1 deprecates every `ToolCallback`-based
  ChatClient method (`forRemoval=true`), so the capability is observed through a recording
  `ToolCallingManager` instead of a `ToolCallback` decorator. That also avoids publishing a
  `ToolCallback` bean, which MCP's converter would otherwise register as a second,
  un-sanitized `search_catalog`.
- Model-generated arguments reach `CatalogService` unchanged, and
  `InvalidCatalogCriteriaException` is rethrown rather than fed back to the model, so
  invalid generated arguments produce the same 400 and message as the REST search endpoint.
- Each request gets its own chat client so the one-call bound and the recording stay
  request-scoped; supplying our own `ToolCallingAdvisor` also makes Spring AI skip its
  automatic one, so nothing else can widen the bound.
- `catalog.ai.enabled=false` or any non-`ollama` provider leaves the assistant service
  absent; the endpoint then returns a sanitized 503 and normal startup is unaffected.

### Defect found and corrected during validation

The first `CatalogAssistantStartupWithoutOllamaTest` run returned **504 instead of 503** for
an unreachable provider. Cause: Spring AI's default retry template (`spring.ai.retry.max-attempts`
= 10, 2 s initial backoff, 5× multiplier) retried connection-refused until the request bound
expired, which both misreported unavailability and violated D5's "no automatic model retry".
Fixed by configuration only — `spring.ai.retry.max-attempts: 0` (documented as one attempt,
no retry). No architecture or contract change was needed.

## 3. Tests

New Slice 14 tests: **31** (232 → 263 total).

| Suite | Tests | Coverage |
| --- | --- | --- |
| `ai.CatalogAssistantServiceTest` | 15 | grounded items/page metadata, arguments actually used, trimmed prompt, two-calls-in-one-turn rejected without executing any, second call in a later turn rejected after exactly one execution, invalid generated arguments rejected by existing validation (and not retried), unsupported intent, malformed tool output, unknown tool name, empty answer, blank/oversized/exact-limit prompt, provider unavailable, provider timeout, sanitized unexpected failure |
| `rest.CatalogAssistantHttpTest` | 9 | documented response shape, 400 blank prompt / unsupported / malformed body / invalid model arguments, 503 unavailability, 504 timeout, sanitized 500, 503 when no provider is configured |
| `ai.CatalogAssistantConfigurationTest` | 5 | enabled by default without credentials, enabled for `ollama`, disabled still starts, unknown provider has no hosted fallback and still starts, approved bounds/defaults |
| `ai.CatalogAssistantStartupWithoutOllamaTest` | 2 | with Ollama unreachable: context starts, health/readiness UP, REST search/detail work, MCP exposes exactly the accepted two tools, and only the assistant returns a sanitized 503 with no diagnostics |

The orchestration tests use a scripted `ChatModel` (no provider) driving the real Spring AI
tool-calling pipeline over the real capability adapter and a mocked `CatalogService`; the
startup test boots the full application against Testcontainers PostgreSQL.

## 4. Commands and results

```bash
mvn -f backend/pom.xml --batch-mode --no-transfer-progress clean verify
```

```text
Tests run: 263, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Baseline before this slice was 232; all retained Phase 1/2 suites still pass unchanged.

```bash
docker compose up --build --wait --wait-timeout 420
docker compose exec -T mcp-catalog-server getent hosts host.docker.internal
docker compose exec -T mcp-catalog-server curl -s -o /dev/null -w '%{http_code}' http://host.docker.internal:11434/api/tags
```

```text
frontend            Up (healthy)  127.0.0.1:4200->8080/tcp
mcp-catalog-server  Up (healthy)  127.0.0.1:8080->8080/tcp
postgres            Up (healthy)  5432/tcp
host.docker.internal -> 172.17.0.1 ; ollama from container HTTP 200
```

Browser regression on the rebuilt stack (`CHROME_BIN=/usr/bin/google-chrome npm --prefix frontend run smoke`):
both smoke sections passed, so the accepted Slice 12–13 UI path is unaffected.

## 5. D9 live acceptance (real Ollama, real PostgreSQL)

```bash
curl -s -X POST http://127.0.0.1:8080/api/v1/catalog/assistant \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"Show me active service items under $200."}'
```

```text
HTTP 200 (48.2 s)
answer     : Here are the active service items under $200: 1. Workstation Setup (SVC-101) - $149.00 …
capability : search_catalog
arguments  : {"type": "SERVICE", "active": true, "maxPrice": 199.99}
provider   : ollama
model      : qwen3-coder-next:latest
page       : 0  pageSize 20  totalItems 6  totalPages 1
items      : (13,SVC-101) (14,SVC-102) (15,SVC-103) (16,SVC-104) (19,SVC-107) (20,SVC-108)
```

Independent PostgreSQL confirmation of the same query:

```text
 id |   sku   |  type   | active | price
 13 | SVC-101 | SERVICE | t      | 149.00
 14 | SVC-102 | SERVICE | t      |  85.00
 15 | SVC-103 | SERVICE | t      |  45.00
 16 | SVC-104 | SERVICE | t      | 199.00
 19 | SVC-107 | SERVICE | t      | 120.00
 20 | SVC-108 | SERVICE | t      |   0.00
```

IDs and SKUs match the assistant's output exactly, and the six items in `items` are the
capability result rather than model text. The model chose `maxPrice = 199.99` for "under
$200", which is its own interpretation and was accepted by service validation.

Live unsupported-intent check:

```text
POST {"prompt":"Write me a haiku about databases."} -> HTTP 400
{"status":400,"error":"Bad Request","message":"This endpoint only supports catalog search requests.","path":"/api/v1/catalog/assistant"}
```

### Local data path evidence

```text
AI_ENABLED=true  AI_PROVIDER=ollama  AI_TIMEOUT=60s
OLLAMA_BASE_URL=http://host.docker.internal:11434  OLLAMA_MODEL=qwen3-coder-next:latest
env | grep -iE "openai|anthropic|azure|gemini|api[_-]?key|token"  -> (none)
```

Ollama's own log shows the Docker backend (`172.20.0.3`) being served locally:

```text
[GIN] 2026/09/24 - 09:26:30 | 200 | 21.42817063s | 172.20.0.3 | POST "/api/chat"
[GIN] 2026/09/24 - 09:26:57 | 200 | 26.325810722s | 172.20.0.3 | POST "/api/chat"
[GIN] 2026/09/24 - 09:27:10 | 200 |  6.59274543s | 172.20.0.3 | POST "/api/chat"
```

The first two entries are the tool-call turn and the answer turn of the successful
scenario (one capability invocation, two model turns); the third is the unsupported-intent
check, which never invoked the capability. All inference happened on the local Ollama
instance, no hosted provider is configured or required, and no credential is present.

## 6. Preserved behavior and security

| Check | Result |
| --- | --- |
| `GET /api/v1/catalog?type=SERVICE&active=true&maxPrice=200` | 200, same 6 IDs |
| `GET /api/v1/catalog/16` / `/99999` / `/0` | 200 / 404 / 400, unchanged envelopes |
| Assistant blank and 1001-character prompts | 400 with the safe envelope, no model call |
| MCP `tools/list` | exactly `[get_catalog_item, search_catalog]` |
| MCP origin/host protection | hostile Origin 403, hostile Host 421 |
| Backend exposure / PostgreSQL | `127.0.0.1:8080` only; `{"5432/tcp":null}` |
| Flyway history / seed rows | unchanged (V1, V2 success; 24 rows) |

No `CatalogService` semantics, accepted REST catalog contract, MCP contract, migration or
security default was changed. No provider type entered the catalog application layer.

## 7. Limitations and explicit exclusions

- Only the **catalog search** workflow is exposed; the endpoint's contract and the
  unsupported-intent message are search-specific, and `get_catalog_item` is deliberately not
  offered as an assistant tool in this slice.
- The provider timeout is enforced by the orchestrator; a timed-out call may still finish
  server-side, but the capability-call bound keeps that to at most one invocation.
- A model turn that shows `answer` text without one successful capability invocation is
  reported as unsupported intent, and a capability result that cannot be parsed is a
  sanitized 500 — the assistant never returns ungrounded catalog data.
- If a model requests more than one capability call in one turn, the whole request is
  rejected without executing any of them.
- Deferred as planned: hosted provider and multi-provider selection (Slice 15), Angular AI
  interaction (Slice 16), conversation history, RAG, streaming, general chat, a standalone
  Java MCP-client example, and any Ollama Compose service.

## 8. Status

**Slice 14 complete.** D4–D7 and the backend portion of D9 are resolved and recorded; real
local Ollama execution invoked the existing catalog capability and returned records matching
PostgreSQL; deterministic tests and the full backend gate pass; the local data path is
recorded above. Nothing was committed, and Slice 15 has not been started.
