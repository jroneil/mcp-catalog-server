# Slice 15 validation — Hosted provider and configuration-based selection

Executed 2026-09-24. **Status: BLOCKED — not accepted.** The implementation, deterministic
tests, Docker/startup/health checks, the Ollama D9 scenario and all regressions pass. The
required **live hosted validation cannot complete** because the approved hosted model
`qwen3.8-max` is not eligible for the current Bailian account (HTTP 403
`AccessDenied.Unpurchased`).

Per the approved instruction, no model was switched after the failure. Full evidence is in
§5.

## 1. Approved decisions

D7 (finalized), D8 and the hosted portion of D9 were approved before implementation and
recorded in [ARCHITECTURE](ARCHITECTURE.md): Bailian / Alibaba Cloud Model Studio (DashScope)
through its OpenAI-compatible API, using the BOM-managed
`spring-ai-starter-model-openai` as the protocol client only, with the logical provider
`bailian`; model `qwen-plus`; `BAILIAN_API_KEY` from the environment only; configuration-only
selection between `ollama` (default) and `bailian`; option 6b startup; `AI_TIMEOUT=60s` with
no retry and no fallback; provider-neutral model metadata; and a provider-neutral failure
classifier.

## 2. Implementation

| Component | Change |
| --- | --- |
| `backend/pom.xml` | Added `spring-ai-starter-model-openai` (no version; BOM-managed, no upgrade) |
| `ai/config/AiProviderEnvironmentPostProcessor` | Maps the logical provider onto `spring.ai.model.chat` (`ollama` → `ollama`, `bailian` → `openai`) so exactly one ChatModel bean exists |
| `META-INF/spring.factories` | Registers that post-processor |
| `ai/config/CatalogAssistantConfiguration` | Split into two provider configurations; each injects its own concrete chat model and its own configured model name |
| `ai/CatalogAssistantService` | Classifier extended provider-neutrally; timeout detection widened for reactive/Netty timeout types |
| `application.yml` | `spring.ai.model.chat`/`embedding` switches, Bailian `openai` client settings (`base-url`, `api-key`, `chat.model`, `timeout`, `chat.max-retries: 0`) |
| `.env.example`, `docker-compose.yml` | `BAILIAN_API_KEY` placeholder and pass-through only; no credential committed |

Design notes:

- The logical provider stays `bailian` everywhere the application reports it; the OpenAI
  module is never presented as the provider in the assistant response.
- Two competing chat models are impossible: the post-processor selects exactly one Spring AI
  chat auto-configuration, and the provider configurations additionally inject concrete
  `OllamaChatModel` / `OpenAiChatModel` types. `spring.ai.model.embedding: none` prevents the
  two embedding auto-configurations from competing, as no embeddings are used.
- **Retry:** the OpenAI-compatible client has its own retry knob
  (`spring.ai.openai.chat.max-retries`, default **3**) separate from the `RetryTemplate`
  disabled in Slice 14. It was set to `0` to honour the approved "no automatic provider
  retry". Without this, an unreachable endpoint was retried and surfaced as a 504 instead of
  the required 503.
- Startup tolerance works because Spring AI's OpenAI setup builds a no-auth client for an
  empty key instead of asserting, so absence of a credential cannot break startup.

## 3. Tests

Slice 15 adds 22 tests (263 → **285**), all passing.

| Suite | Tests | Coverage |
| --- | --- | --- |
| `ai.config.AiProviderEnvironmentPostProcessorTest` | 6 | ollama/bailian mapping, default, case/whitespace, unknown provider, source-independence |
| `ai.config.CatalogAssistantProviderSelectionTest` | 3 | real auto-configurations: exactly one ChatModel per provider, no competing bean, hosted start without a credential, configuration-only switching |
| `ai.CatalogAssistantConfigurationTest` | 6 | local/hosted/disabled/unsupported provider selection, approved defaults |
| `ai.CatalogAssistantProviderFailureTest` | 6 | `TransientAiException`, `NonTransientAiException`, reactive client failure, timeout, sanitized unexpected failure, capability never invoked on provider failure |
| `ai.CatalogAssistantHostedStartupTest` | 3 | full application against Testcontainers PostgreSQL with `AI_PROVIDER=bailian`, credential absent and endpoint unreachable: starts, health UP, REST/MCP unchanged, only the assistant returns a sanitized 503, OpenAI-compatible model selected and no Ollama model bean |
| `ai.CatalogAssistantServiceTest` | 16 | Slice 14 suite plus the provider/model metadata and "same contract in both modes" case |
| `rest.CatalogAssistantHttpTest` | 11 | Slice 14 suite plus the hosted-mode contract and a credential/diagnostic leakage check |

Every requested deterministic test is covered: provider selection, configuration-only
switching, local mode without a hosted credential, hosted mode with a missing credential,
sanitized 503/504, no retry/fallback, one capability invocation, identical response
contract, provider/model metadata, invalid-argument validation and existing REST/MCP
regression.

```bash
mvn -f backend/pom.xml --batch-mode --no-transfer-progress clean verify
```

```text
Tests run: 285, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 4. Docker, startup, health and local-mode acceptance

```bash
docker compose up --build --wait --wait-timeout 420
```

```text
frontend            Up (healthy)  127.0.0.1:4200->8080/tcp
mcp-catalog-server  Up (healthy)  127.0.0.1:8080->8080/tcp
postgres            Up (healthy)  5432/tcp
```

Ollama D9 scenario on the Slice 15 build (`AI_PROVIDER=ollama`, no hosted credential
present), *"Show me active service items under $200."*:

```text
HTTP 200 (42.2 s)
provider   : ollama
model      : qwen3-coder-next:latest
capability : search_catalog
arguments  : {"type": "SERVICE", "active": true, "maxPrice": 200}
items      : (13,SVC-101) (14,SVC-102) (15,SVC-103) (16,SVC-104) (19,SVC-107) (20,SVC-108)
page meta  : 0 20 6 1
```

Independent PostgreSQL confirmation returned exactly `13,SVC-101 … 20,SVC-108`. Local mode
therefore still satisfies FR-12 and works with `BAILIAN_API_KEY` absent.

Regression and security: REST search 200, detail 200/404/400 unchanged, MCP exposes exactly
`[get_catalog_item, search_catalog]`, hostile Origin 403 / hostile Host 421, backend
`127.0.0.1:8080` only, PostgreSQL unpublished. Frontend browser smoke passed both sections.
No credential material was found in container logs (0 suspicious matches). Flyway/seed data
unchanged.

## 5. Blocking live hosted evidence

### 5.1 Approved model (`qwen3.8-max`) — access denied

D8 was updated to `qwen3.8-max` and the probe was re-run once using the application's own
`search_catalog` definition taken from the running MCP server (name, description and the
exact `inputSchema` with `type`, `active`, `maxPrice`, `text`, `page`, `pageSize`):

```bash
POST https://token-plan.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1/chat/completions
  model=qwen3.8-max, messages=[user: "Show me active service items under $200."],
  tools=[existing search_catalog], max_tokens=256
```

```text
HTTP 403
{"error":{"code":"AccessDenied.Unpurchased","type":"invalid_request_error",
          "message":"Access to model denied. Please make sure you are eligible for using the model."}}
```

The model is listed in the workspace catalog but this account is not eligible for it, so the
required native tool call cannot be demonstrated. Per the approved instruction, **no other
model was tried**: `qwen3.7-plus`, `qwen3.8-flash`, DeepSeek and the remaining catalog models
were explicitly not probed or substituted.

Three probes now frame this account's hosted access:

| Model / endpoint | Result |
| --- | --- |
| `qwen-plus` on the workspace endpoint | HTTP 404 `model_not_found` — not in the catalog |
| `qwen3.8-max` on the workspace endpoint | HTTP 403 `AccessDenied.Unpurchased` — not eligible |
| `qwen-plus` on the legacy international endpoint | HTTP 401 `invalid_api_key` — key is workspace-bound |

The account therefore has a working, authenticated workspace endpoint but **no hosted model
with confirmed eligibility**, which is why the hosted D9 gate stays open. The remaining
options are account-side (enable/purchase access for the approved model, or supply a
standard API key with model eligibility) rather than code-side.

### 5.2 Earlier evidence

Credential used from the existing local Bailian configuration (value never printed, copied
or logged; injected only into the process environment). Endpoint derived from the official
Model Studio documentation *and* the local configuration: the configured host is
`https://token-plan.ap-southeast-1.maas.aliyuncs.com`, and the documented OpenAI-compatible
base URL appends `/compatible-mode/v1` (Singapore region; the base URL excludes
`/chat/completions`).

```bash
POST https://token-plan.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1/chat/completions
  model=qwen-plus, one tool definition, max_tokens=128
```

```text
HTTP 404
{"error":{"code":"model_not_found","type":"invalid_request_error","message":"Model not exist."}}
```

The same endpoint authenticates correctly and lists the workspace's available models:

```bash
GET .../compatible-mode/v1/models   -> HTTP 200
```

```text
auto, deepseek-v4-flash-0731, deepseek-v4-pro, deepseek-v4.1-flash, glm-5.2, glm-5.3,
qwen-audio-3.0-realtime-plus, qwen-audio-3.0-tts-plus, qwen3.6-flash, qwen3.7-max,
qwen3.7-plus, qwen3.8-flash, qwen3.8-max, wan2.7-image, wan2.7-image-pro
```

`qwen-plus` is not in that catalog, so it cannot perform the tool call. The legacy
international endpoint is not an alternative for this credential:

```bash
POST https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions (qwen-plus)
  -> HTTP 401 invalid_api_key
```

This matches the documented rule that a Model Studio API key is bound to the region and
workspace of the endpoint it calls, which is why the workspace-specific base URL is required
for this account. `BAILIAN_BASE_URL` is therefore set to that workspace URL at run time only:
the committed default stays the official generic endpoint
(`https://dashscope-intl.aliyuncs.com/compatible-mode/v1`), so repository configuration
remains portable and carries no account-specific endpoint.

No hosted D9 result can be recorded while the approved model is ineligible, so the D8 and
hosted D9 acceptance criteria — and the "switching Ollama → Bailian requires configuration
only" demonstration with a real hosted result — remain **unmet**.

## 6. Explicit exclusions

No additional hosted provider, no automatic fallback, no runtime provider-selection UI, no
provider framework beyond the two Spring AI chat-model selections, no Slice 16 work, and no
change to `CatalogService`, migrations, MCP contracts, the catalog REST contracts, the
assistant response schema or `POST /api/v1/catalog/assistant` semantics.

## 7. Status

**Blocked.** Implementation and all deterministic gates pass (285 tests); local Ollama mode
is re-validated end to end against PostgreSQL; Docker, health, security and browser
regression are unchanged. The live hosted validation is blocked on the approved model not
being eligible for the current Bailian account, and no model was changed without approval.
Nothing was committed.

### Acceptance on hold

Slice 15 hosted-provider acceptance is **on hold** pending external hosted-model entitlement.
Under that hold:

- the Slice 15 implementation is retained unchanged — provider selection, the
  OpenAI-compatible client configuration, the failure classifier extension and the
  `spring.ai.openai.chat.max-retries=0` fix all stay in place;
- **no further hosted-provider calls are made**;
- ongoing development uses **local mode only** — `AI_PROVIDER=ollama` and
  `OLLAMA_MODEL=qwen3-coder-next:latest`, which are the committed defaults, so no
  configuration change is needed;
- provider selection, Bailian, hosted credentials and hosted validation are out of scope
  until explicitly requested again.

The block is therefore an external entitlement dependency, not a code, architecture or
contract issue: the workspace endpoint authenticates and the integration itself is complete
and tested.
