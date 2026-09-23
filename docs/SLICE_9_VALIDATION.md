# Slice 9 validation

Executed 2026-09-23. **Slice 9 passed** via the Ollama-backed local host. Slice 10 has
not been started.

A real MCP-capable host (**opencode 1.18.23**) using a **local Ollama model**
(`qwen3-coder-next:latest`) connected to the running Dockerized server over Streamable
HTTP, discovered exactly the two production tools, read their schemas, selected the
correct tool for four natural-language prompts, invoked it, and answered from the
returned PostgreSQL data — including correctly reporting a not-found error without
fabricating an item. Every step ran against the local model; `cost=0` on every step and
no hosted provider was used.

This supersedes the earlier BLOCKED status, which was caused solely by Claude Code in
this environment having no account credentials (see "Claude Code attempt" below).

## 1. Production code changes

**None.** No production source, configuration, dependency, migration or Docker file was
changed in either the Claude Code attempt or this Ollama-backed validation. The
repository contains only documentation changes plus `docs/SLICE_9_VALIDATION.md`. All
host configuration lives outside the repository (section 6).

## 2. Host used

**opencode** (`opencode-ai`) — an already-installed, local, MCP-capable agent host. It
was preferred over the alternatives because it is already installed, supports remote
MCP servers natively, supports local Ollama models natively, and offers a headless
`opencode run` mode that emits raw JSON events (tool name, arguments, raw result).

Host survey performed before configuring anything:

| Candidate | Installed | Streamable HTTP MCP | Tool discovery/invocation | Can use Ollama | Outcome |
| --- | --- | --- | --- | --- | --- |
| opencode 1.18.23 | yes | yes (`mcp.type=remote`) | yes | yes (native `ollama` provider) | **selected** |
| Claude Code 2.1.209 | yes | yes (verified connection) | yes | not without a translation proxy | blocked: no credentials |
| Open WebUI 0.11.3 | yes, running on :8765 | n/a | n/a | yes | not needed; requires auth setup |
| bailian-cli | yes | n/a | n/a | no (hosted Alibaba Cloud) | rejected — hosted, not local |

## 3. Host version

```text
opencode 1.18.23
```

## 4. Ollama model used

```text
qwen3-coder-next:latest   (79.7B, 51 GB — already installed)
```

Model selection from the five already-installed Ollama models, using Ollama's own
capability report:

| Model | Capabilities | Verdict |
| --- | --- | --- |
| `qwen2.5-coder:14b` | completion, **tools**, insert | tried; Ollama returned tool arguments as plain text, not a `tool_calls` array |
| `qwen3-coder-next:latest` | completion, **tools** | **selected** — returns real native `tool_calls` with correct arguments |
| `gemma3:12b` | completion, vision | no tool support |
| `deepseek-coder:latest` | completion | no tool support |
| `nomic-embed-text` | embeddings | not an agent model |

No model was downloaded; an already-installed model was used, as required. The 79.7B
model is slower (≈2 minutes per scenario on this host, RTX 3060 12 GB + 123 GB RAM) but
is the only installed model whose Ollama tool-call parsing actually works, which was
verified before running any scenario.

## 5. Ollama endpoint

```text
http://127.0.0.1:11434         (Ollama 0.32.0, /usr/local/bin/ollama)
```

opencode discovered the provider automatically (`opencode models` lists
`ollama/qwen2.5-coder:14b`, `ollama/qwen3-coder-next:latest`,
`ollama/deepseek-coder:latest`). No API key or credential is needed for the local
endpoint.

## 6. Exact host configuration

Created `/tmp/opencode-slice9/opencode.json` (project-local to a scratch directory,
**outside the repository**):

```json
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "catalog": { "type": "remote", "url": "http://127.0.0.1:8080/mcp", "enabled": true }
  },
  "tools": {
    "bash": false, "edit": false, "write": false, "read": false, "grep": false, "glob": false,
    "apply_patch": false, "todowrite": false, "webfetch": false, "websearch": false, "question": false,
    "skill": false, "explore": false, "general": false, "task": false, "list": false, "search": false
  }
}
```

| Property | Value |
| --- | --- |
| Configuration scope | **Temporary / project-local** scratch directory (`/tmp/opencode-slice9`); not committed, not in the repository |
| Files modified outside the repository | `/tmp/opencode-slice9/opencode.json` (created). The pre-existing user config `~/.config/opencode/opencode.json` was **read but not modified**; it contains an unrelated hosted provider entry that was never selected for this validation |
| Restart/reload required | No — `opencode mcp list` reflected the server immediately |
| Model pinned per run | `-m ollama/qwen3-coder-next:latest` on every invocation |

Why the `tools` block: opencode's default agent is a *coding* agent, and in an empty
scratch directory the model spent its turns on unrelated built-ins (`glob`, `read`,
`task`) instead of the catalog. Disabling the irrelevant built-ins removes that
distraction. **Both catalog tools remained enabled**, so the model still had to choose
between `catalog_search_catalog` and `catalog_get_catalog_item` from the prompt; no tool
call, tool name or argument was ever specified in any prompt.

The application's production configuration was **not** modified for the host, and no
Origin/Host allowlist change was needed (opencode is a non-browser client and sends no
`Origin`; its `Host` is loopback).

## 7. Transport and MCP endpoint

```text
Transport: Streamable HTTP (opencode MCP type "remote")
Endpoint:  http://127.0.0.1:8080/mcp
Protocol:  MCP 2025-11-25 (negotiated)
```

`opencode mcp list`:

```text
●  ✓ catalog connected
      http://127.0.0.1:8080/mcp
```

Server-side confirmation (`docker compose logs mcp-catalog-server`):

```text
Client initialize request - Protocol: 2025-11-25, Info: Implementation[name=opencode, version=1.18.23]
```

## 8. Discovered tool inventory

The host discovered exactly the two production tools, exposed as
`catalog_search_catalog` and `catalog_get_catalog_item` (opencode prefixes MCP tools with
the server name). Both were invoked successfully during the scenarios; no third MCP tool
exists and none was offered.

## 9. Schema visibility

Asked the host to describe its catalog tools without calling them. Its answer reproduced
the published contract exactly, including the bounds that exist only in the parameter
descriptions:

```text
catalog_search_catalog
  type (string, optional): Filter by PRODUCT or SERVICE
  active (boolean, optional): Filter by active status
  maxPrice (number, optional): Maximum price ceiling (0 to 9999999999.99)
  text (string, optional): Case-insensitive substring search against SKU, name, description (max 200 chars)
  page (integer, optional): Zero-based page number (default: 0)
  pageSize (integer, optional): Items per page (1–100, default: 20)

catalog_get_catalog_item
  id (integer, required): Positive integer catalog item identifier
```

The host also reported: **"I have no access to MCP resources or prompts."** — matching
the server's advertised capabilities (only `logging` and `tools`; `resources`, `prompts`
and `completions` are disabled), so no Phase 3 feature is exposed as a usable host
feature.

## 10. Scenario A result

**Prompt (natural language, no tool call specified):** `Find all active service items under $200.`

| Item | Evidence |
| --- | --- |
| Tool selected | `catalog_search_catalog` ✅ correct |
| Arguments | `{"type":"SERVICE","active":true,"maxPrice":200}` ✅ exactly as expected |
| Raw tool result | 6 items, ids `13,14,15,16,19,20` (`SVC-101` 149.00, `SVC-102` 85.00, `SVC-103` 45.00, `SVC-104` 199.00, `SVC-107` 120.00, `SVC-108` 0.00) |
| Host final response | "Active service items under $200: SVC-101 Workstation Setup ($149.00) … SVC-108 Initial Technology Consultation ($0.00)" |
| Grounded | ✅ every SKU, name and price matches the tool result |
| Unsupported facts | none — no invented items, prices, descriptions or statuses |
| Tool selection correct | ✅ |

## 11. Scenario B result

**Prompt:** `Show me catalog item 16.`

| Item | Evidence |
| --- | --- |
| Tool selected | `catalog_get_catalog_item` ✅ correct (detail tool, not search) |
| Arguments | `{"id":16}` ✅ |
| Raw tool result | `{"id":16,"sku":"SVC-104","name":"Network Health Assessment","type":"SERVICE","description":"Review office network configuration and provide a prioritized findings report.","price":199.00,"active":true,"createdAt":"2026-01-15T09:00:00Z","updatedAt":"2026-01-15T09:00:00Z"}` |
| Host final response | "Catalog item 16 is "Network Health Assessment" (SVC-104), a SERVICE priced at $199.00. It was created and last updated on 2026-01-15." |
| Grounded | ✅ sku, name, type, price and active state all from the tool result |
| Unsupported facts | none |
| Tool selection correct | ✅ |

## 12. Scenario C result

**Prompt:** `Find product items containing keyboard.`

Ground truth in the seed data: **exactly one** product matches — id 8 / `PRD-108`
"Standing Desk Converter", matched through its **description** ("…supporting a monitor
and keyboard."). This is a deliberate grounding trap: the match is not in the name.

| Item | Evidence |
| --- | --- |
| Tool selected | `catalog_search_catalog` ✅ correct |
| Arguments | `{"type":"PRODUCT","text":"keyboard"}` ✅ exactly as expected |
| Raw tool result | `{"items":[{"id":8,"sku":"PRD-108","name":"Standing Desk Converter","type":"PRODUCT","description":"Height-adjustable desktop platform supporting a monitor and keyboard.","price":329.00,"active":true,…}],"page":0,"pageSize":20,"totalItems":1,"totalPages":1}` |
| Host final response | "Found 1 product: **Standing Desk Converter** (PRD-108) — $329.00" |
| Grounded | ✅ matches the single seeded row exactly |
| Unsupported facts | none — it did **not** invent a keyboard product or add keyboard specs, stock or availability |
| Tool selection correct | ✅ |

## 13. Scenario D result

**Prompt:** `Show me catalog item 99999.`

| Item | Evidence |
| --- | --- |
| Tool selected | `catalog_get_catalog_item` ✅ correct |
| Arguments | `{"id":99999}` ✅ |
| Raw tool result | tool status **error**, message `Catalog item not found: 99999` (the server's defined not-found error) |
| Host final response | "No catalog item with ID 99999 was found." |
| Grounded | ✅ accurately reflects the tool error |
| Unsupported facts | none — no fabricated item, no invented fields |
| Tool selection correct | ✅ |

## 14. Tool-selection quality

| Scenario | Expected tool | Selected | Correct |
| --- | --- | --- | --- |
| A | `search_catalog` | `catalog_search_catalog` | ✅ |
| B | `get_catalog_item` | `catalog_get_catalog_item` | ✅ |
| C | `search_catalog` | `catalog_search_catalog` | ✅ |
| D | `get_catalog_item` | `catalog_get_catalog_item` | ✅ |

4/4 correct. The model distinguished list-search intent (A, C) from single-item lookup
intent (B, D) without any tool name being mentioned in the prompts, and derived every
argument (`type`, `active`, `maxPrice`, `text`, `id`) and its type from the schemas.

## 15. Grounding / hallucination observations

| Failure mode | Observed |
| --- | --- |
| Wrong tool | none |
| Invented catalog data | none |
| Changed prices | none (`149.00`, `85.00`, `45.00`, `199.00`, `120.00`, `0.00`, `329.00` all exact) |
| Invented descriptions | none |
| Invented availability/status | none |
| Claimed a missing item exists | none — Scenario D correctly reported not found |
| Ignored a tool error | none — Scenario D surfaced the error in its answer |
| Answered from model knowledge instead of tool data | none — the seed catalog is fictional and no answer could be produced without the tool |

Minor observations, not failures: in Scenario C the host omitted the reason the item
matched (the description) and the `active` flag, both present in the tool result — that
is summarisation, not fabrication. Scenario B's summary added nothing beyond the record.

## 16. Local-model / privacy-path verification

| Check | Result |
| --- | --- |
| Model endpoint actually used | local Ollama — `~/.local/share/opencode/log/opencode.log` records `llm.runtime=ai-sdk llm.provider=ollama llm.model=qwen3-coder-next:latest` for these runs |
| Ollama URL | `http://127.0.0.1:11434` (loopback only) |
| Hosted credential required | none — `opencode auth list` reports **0 credentials** |
| Hosted provider configured for this validation | none — the only provider referenced in any run is `ollama`; the model was pinned per invocation |
| Pre-existing hosted configuration | the user-level `~/.config/opencode/opencode.json` already contained an unrelated hosted provider (Alibaba Cloud Model Studio) with a key. It was **read but never selected or modified**, and no credential from it was used |
| Cost of all steps | `cost=0` on every `step_finish` event in all five runs |
| Privacy claim scope | only that this validation performed no hosted-provider inference and needed no hosted credential. No broader claim is made |

## 17. Security regression results

After host validation, with defaults untouched:

| Check | Result |
| --- | --- |
| absent `Origin` (non-browser client) | HTTP 400 — accepted, body rejected |
| `Origin: http://localhost:6274` | HTTP 400 — accepted by the loopback wildcard |
| `Origin: http://evil.example` | HTTP **403** `Invalid Origin header` |
| `Host: evil.example` | HTTP **421** `Invalid Host header` |
| backend publication | `127.0.0.1:8080` only |
| PostgreSQL publication | `5432/tcp` = `null` (unpublished) |
| permanent security broadening | none — `application.yml`, Compose and the allowlists are unmodified (`git status` clean apart from documentation) |

## 18. Maven / test results

`mvn clean verify`: **BUILD SUCCESS — 184 tests, 0 failures, 0 errors, 0 skips** (before
and after host validation). No test was added, removed or modified in this slice.

## 19. Docker / health / data results

- `docker compose up --build --wait` passed; backend and PostgreSQL both healthy;
  `Registered tools: 2`.
- Aggregate health HTTP 200; readiness HTTP 200, both `UP`.
- `flyway_schema_history`: V1 checksum `162354501`, V2 checksum `-2124124441`, both
  `success=t` — unchanged, no migration added.
- `catalog_item`: 24 rows — unchanged.
- Every tool result returned to the host matched the seeded rows exactly.

## Claude Code attempt (context for the earlier BLOCKED status)

Claude Code 2.1.209 was the originally selected host. It **did connect** — `claude mcp
list` reported `✔ Connected` and the server logged `Implementation[name=claude-code,
version=2.1.209]` completing the MCP `initialize` handshake — but natural-language runs
failed with `{"is_error":true,"terminal_reason":"api_error","result":"Not logged in ·
Please run /login","num_turns":1}`. With no `ANTHROPIC_API_KEY`, no
`~/.claude/credentials.json` and no `oauthAccount`, that installation has no credentials,
so it could not exercise the model stage. Claude Code also cannot use Ollama directly
(it requires an Anthropic-compatible endpoint, and adding a translation proxy would have
meant introducing a new framework). This was a **host-account limitation**, not a
transport, configuration or server defect, so no production code was changed. The
registration remains in `~/.claude.json` (project `/tmp`) and can be removed with
`claude mcp remove catalog -s local`; it is unrelated to the opencode configuration used
for the passing validation.

## 20. Deviations from PRD/plan

- The plan's Slice 9 host decision named Claude Code "on the same host". That host is
  installed but unauthenticated in this environment, so the validation was completed with
  the next-best already-installed MCP-capable local host that can also use Ollama, as the
  task explicitly permits ("another already-installed local MCP-capable client that can
  use Ollama"). No other deviation.
- The host tool-set was narrowed to remove unrelated coding tools (section 6). This is
  host configuration for a focused validation, not a change to the server, and both
  catalog tools stayed enabled.
- PRD FR-6 ("at least one real MCP-compatible host shall successfully connect … and
  invoke a catalog tool") is now satisfied, including for the local-model path.
- PRD FR-10 (Ollama support) is a **Phase 2 application feature** and was **not**
  implemented; this slice only used Ollama as the *host's* model, which does not add any
  AI provider integration to the application.

## 21. Unresolved risks / questions

1. **Model quality is host-side.** `qwen2.5-coder:14b` (faster, 14B) cannot drive tool
   calls through Ollama in this environment — its output arrives as text — while
   `qwen3-coder-next:latest` (79.7B) works but takes ~2 minutes per scenario. This is a
   model/Ollama-template limitation, not a server issue, and it bounds how practical a
   recurring local-agent validation would be on this hardware.
2. **Tool-set narrowing.** Scenarios were run with unrelated built-in tools disabled. The
   same prompts with the full coding tool-set made the model wander (it tried `glob` and
   non-existent tools) rather than call the catalog — a host-prompting observation worth
   remembering when wiring real agents to this server.
3. **Claude Code remains unvalidated for prompts** until that installation is
   authenticated (`/login` or `ANTHROPIC_API_KEY`); the MCP connection half is already
   proven.
4. **Phase 2 provider integration is still absent by design.** The application itself
   still has no Ollama or hosted-AI integration; this slice deliberately did not add one.
5. Inherited risks unchanged: SDK schema-validation wording, no
   `outputSchema`/`structuredContent`, and the transport-bean override tracking Spring AI
   upgrades.

No rollback was needed and no server defect was found. **Slice 10 has not been started.**
