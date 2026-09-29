# Slice 17 — Phase 2 Acceptance Audit and Documentation

Executed 2026-09-25. **Local audit activities complete; overall acceptance NOT COMPLETE.**

- Slice 16: **COMPLETE for local Ollama acceptance** (unchanged).
- Slice 15 hosted acceptance: **ON HOLD** (unchanged).
- Phase 2 both-provider acceptance: **OUTSTANDING**.
- Slice 17: **NOT STARTED at task start**; now local audit activities complete, with hosted-dependent acceptance blocked and one genuine implementation defect (A17-01).
- No hosted-provider acceptance claim. No hosted calls or credential investigation.
- No implementation behavior changed, no Phase 3 work, no commit.

## 1. Scope and authority

This audits implementation plan v0.2 Slice 17 against PRD v0.3 FR-7–FR-13A, supporting
§§9–17, documentation §19, Phase 2 deliveries/exit criteria §21, DoD §22 and scenarios §23.
The user authorized all non-hosted audit activities despite the normal dependency on
accepted Slices 11–16. This does not resolve the Slice 15 hold or waive either-provider
acceptance. D9 is resolved for the local UI scenario only.

The starting working tree contained only the uncommitted documentation clarification in
`docs/SLICE_16_VALIDATION.md`; it was preserved byte-for-byte. Any already-delivered
Slice 15/16 implementation was also preserved. A SHA-256 baseline of tracked files was
taken before edits; the final comparison permits only the documentation changes below.
No historical validation record was edited by Slice 17.

## 2. Result and defect

All executed regression/build/runtime functional gates passed: **285 backend tests**, **59
frontend tests**, Angular production build, Compose configuration/build/start/health,
REST/MCP equivalence/security, conventional browser search/detail and real local Ollama
browser workflow. This establishes local functional evidence, not full acceptance.

### A17-01 — Raw provider exceptions disclose sensitive diagnostic content in logs: FAIL

**Owning slices:** Slice 14 assistant exception logging; Slice 15 provider failure handling
and regression coverage. This is separate from the hosted entitlement hold and applies
to the shared assistant failure path, not only hosted operation.

**Implementation:** `backend/src/main/java/com/example/mcpcatalog/ai/CatalogAssistantService.java:200`
logs `logger.warn("Catalog assistant provider call failed", failure)` before classifying
and sanitizing the caller-facing exception. The raw throwable is not redacted.

**Reproduction without providers or credentials:** the unchanged
`CatalogAssistantProviderFailureTest.mapsTransientAiFailuresToASanitized503` and
`mapsNonTransientAiFailuresToASanitized503` inject scripted exceptions containing the
synthetic marker `sk-secret-value`. The new full Maven run's log contains both
`quota exceeded for key sk-secret-value` and
`Incorrect API key provided (Bearer sk-secret-value)`. It also contains the existing
synthetic `raw provider payload sk-secret` fixture. These are known test fixtures, not
real credentials; no credential investigation was performed.

**Impact:** response sanitization passes, but PRD §§11/15/17 and the plan's no-secrets-in-logs
acceptance requirement are not satisfied. Existing tests assert caller errors, not safe
log output. Passing them cannot establish the logging requirement. There is no claim that
an actual credential leaked during this audit.

**Disposition:** acceptance stopped for this criterion and aggregate DoD. No fix or new
test was made in this audit. Return remediation and log-sanitization regression coverage
to the owning AI slice under separate authorization. Documentation's unconditional
"never logged" claim was corrected. Historical acceptance records remain unchanged.

## 3. Evidence key

Paths below are relative to the repository. Evidence keys in the matrix refer to these
concrete artifacts, not assumed behavior.

| Key | Evidence |
| --- | --- |
| I-UI | `frontend/src/app/catalog-search.*`, `catalog-detail.*`, `catalog-api.ts`, routes/state; standalone Angular, relative REST, presentation-only |
| I-REST | `backend/src/main/java/com/example/mcpcatalog/rest/CatalogController.java`, advice/error types; controller delegates to CatalogService |
| I-CORE | `catalog/application/CatalogService.java`, criteria/page/view types; `catalog/persistence/`; immutable Flyway V1/V2 under backend resources |
| I-MCP | `mcp/tools/SearchCatalogTool.java`, `GetCatalogItemTool.java`, sanitizing adapters and MCP configuration; accepted two-tool contract |
| I-AI | `ai/CatalogAssistantService.java`, recorder/tool manager, REST assistant controller/result; authoritative items/page taken from capability output |
| I-CONFIG | `ai/config/CatalogAssistantConfiguration.java`, `AiProviderEnvironmentPostProcessor.java`, `application.yml`; one server-side ChatModel selected, no fallback |
| I-ASSIST | `frontend/src/app/catalog-assistant*`; prompt-only relative POST, answer/items/error/retry/cancellation; no browser provider configuration |
| I-RUN | root Compose, backend/frontend Dockerfiles, frontend nginx/dev proxy; three services, readiness dependency, same-origin REST, loopback/private database |
| I-PINS | backend POM, frontend package.json/package-lock.json and Docker image tags; existing pins unchanged |
| T-B | Fresh full Maven clean verify: 285 tests, 0 failures/errors/skips; XML reports under `backend/target/surefire-reports` |
| T-REST | `CatalogControllerTest`, `CatalogRestIntegrationTest` (37 cases), `McpArchitectureBoundaryTest`; real PostgreSQL REST/MCP equivalence and validation |
| T-CORE | `CatalogServiceTest`, `CatalogServicePostgresTest`, `CatalogPersistenceTest`, `FlywayStartupFailureTest`; PostgreSQL/Testcontainers |
| T-MCP | transport security, wiring/discovery, search/detail MCP integration, internal-error sanitization, `PhaseOneEndToEndMcpTest`; all in T-B |
| T-AI | assistant service/HTTP/configuration/failure tests and startup-without-provider tests; scripted models and unreachable loopback endpoints, no live hosted calls |
| T-SELECT | `AiProviderEnvironmentPostProcessorTest`, `CatalogAssistantProviderSelectionTest`, `CatalogAssistantHostedStartupTest`; configuration-only selection/startup assertions, not hosted acceptance |
| T-F | Fresh `npm test`: 59 cases in API/search/detail/assistant suites; no changed tests |
| T-BUILD | Fresh production build, 285.05 kB initial bundle, no budget warning; full Maven package succeeds |
| R-COMPOSE | Fresh Compose config/build/up --wait, all three healthy; Actuator aggregate/readiness UP; unchanged publication |
| R-PROTOCOL | Live MCP initialize/discover/search/detail plus REST equality, 400/404, Origin 403, Host 421; details in §5 |
| R-UI | Existing `catalog-smoke.mjs`, real nginx → REST → PostgreSQL search/detail, validation/empty/offline/retry/history/mobile checks |
| R-AI | Existing `catalog-assistant-smoke.mjs`, real Ollama, HTTP 200, six expected IDs/SKUs, same-origin browser requests, detail/return and screenshots |
| R-DB | Independent PostgreSQL query: six expected active services; 24 seed rows; Flyway versions 1/2 successful |
| H-P1 | Slice 10 accepted Phase 1 audit; Slice 8 Inspector 2.7.0 and Slice 9 opencode 1.18.23/local Ollama real-host evidence retained, not rerun or rewritten |
| H-HOLD | Slice 15 validation's authoritative external-entitlement hold; no successful real hosted demonstration, and no retry during this audit |
| D | README, ARCHITECTURE, TEST_PLAN, versioned PRD/plan, frontend README and this audit; current documentation drift corrected |

## 4. Requirement-to-evidence matrix

IDs suffixed with `/local`, `/hosted` or descriptive labels split a PRD requirement for
precise evidence; they are audit identifiers, not new product requirements. A combined
both-provider requirement remains blocked even when its local row passes.

### Functional requirements

| Requirement / criterion | Concise requirement | Owning slice | Implementation | Automated tests | Runtime/manual evidence | Provider mode | Status | Limitation/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FR-7 | List/search, type/active/max-price filters, readable results and detail | 12–13 | I-UI | T-F | R-UI | None | PASS | Read-only; no admin writes assigned |
| FR-8 | Versioned REST uses CatalogService | 11 | I-REST | T-REST | R-PROTOCOL | None | PASS | Accepted binding semantics retained |
| FR-9 | Equivalent REST/MCP share persisted data and service | 11 | I-REST/I-CORE/I-MCP | T-REST, T-CORE | R-PROTOCOL, R-DB | None | PASS | Decimal query scale vs JSON normalization remains documented |
| FR-10 | Externally configured local Ollama | 14 | I-CONFIG/I-AI | T-AI | R-AI | Ollama | PASS — LOCAL OLLAMA | Existing host installation required |
| FR-11/selection | Configuration-only provider selection, isolated from catalog/MCP | 15 | I-CONFIG | T-SELECT | Source/config review; only local selection run | Both, deterministic | PASS | Selection evidence alone is not hosted execution |
| FR-11/hosted | A real hosted provider works | 15 | I-CONFIG/I-AI exists | T-AI/T-SELECT | H-HOLD; no successful hosted run | Hosted | BLOCKED — HOSTED ACCEPTANCE ON HOLD | Missing real successful model/tool/result execution |
| FR-12 | Local workflow needs no hosted prompt/business transmission | 14/16 | I-CONFIG/I-AI/I-ASSIST; only selected model wired | T-SELECT, T-F | R-AI, runtime local metadata, R-DB | Ollama | PASS — LOCAL OLLAMA | Architecture/observed path evidence, not packet-capture or general privacy certification; A17-01 affects logs separately |
| FR-13/local; D9/local | Natural-language input invokes capability and renders persisted items | 14/16 | I-AI/I-ASSIST | T-AI, T-F | R-AI, R-DB | Ollama | PASS — LOCAL OLLAMA | Slice 16 remains locally COMPLETE |
| FR-13/both; D9/hosted | Same human-readable workflow with both real providers | 15–16 | I-AI/I-ASSIST | Deterministic T-AI/T-F only | Local R-AI; hosted evidence absent | Both | BLOCKED — HOSTED ACCEPTANCE ON HOLD | Missing real hosted browser → assistant → capability → PostgreSQL result demonstration |
| FR-13A | Same-origin frontend and dev proxy; no permissive CORS | 12/16 | I-RUN, relative clients | T-F | R-UI/R-AI requests stay on frontend origin; nginx /mcp 404 | None/local | PASS | No CORS change or direct browser provider/MCP access |

### Supporting requirements and architecture

| Requirement / criterion | Concise requirement | Owning slice | Implementation | Automated tests | Runtime/manual evidence | Provider mode | Status | Limitation/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| §9; AC-1–AC-5, AC-7 | Separate adapters, service, persistence and provider orchestration | 1–16 | I-REST/I-CORE/I-MCP/I-AI | T-REST boundary tests, T-CORE | Static review; R-PROTOCOL/R-AI | Both design; local runtime | PASS | No added provider abstraction or internal MCP self-client |
| AC-6; FR-3A | Explicit, stable MCP names/schema/result contracts | 4–7/11 | I-MCP | T-MCP contract assertions | R-PROTOCOL discovers exactly two tools | None | PASS | No contract mutation |
| AC-8; §§10/14 | Untrusted criteria validated; bounds, pagination, not-found, safe client errors | 3/5–7/11/14/16 | I-CORE, REST/MCP/assistant advice | T-CORE/T-REST/T-MCP/T-AI/T-F | R-PROTOCOL 400/404; R-UI validation | None/local | PASS | Client sanitization does not establish safe logs |
| §10/backend/frontend/pins | Accepted Java/Angular/Maven/Spring/driver/toolchain versions | 1/4/12/14–15 | I-PINS | T-B/T-F/T-BUILD | Compose image builds | None | PASS | No upgrades made by audit |
| §10/database/search | Real PostgreSQL, immutable migrations, bounded simple SQL search | 2–3 | I-CORE | T-CORE | R-DB; unchanged migration hashes | None | PASS | No H2, full-text search or schema change |
| §§11/12/startup | Frontend/backend/PostgreSQL start, seed/readiness and REST data work | 1–2/12 | I-RUN | T-B context and fresh-container migrations | R-COMPOSE/R-UI/R-DB; H-P1 clean-volume provenance | None | PASS | Current Compose reused volume; fresh DB path exercised by Testcontainers, not destructive volume reset |
| §§11/15/network | Loopback HTTP defaults, private DB, MCP Origin/Host protection | 1/4/12 | I-RUN, MCP security config | T-MCP | R-COMPOSE ports; R-PROTOCOL 403/421; frontend /mcp 404 | None | PASS | Internal container wildcard listeners do not publish all host interfaces |
| §§11/15/config | External config; browser contains no provider secrets/configuration | 1/12/14–16 | I-CONFIG/I-ASSIST; ignored .env, placeholder configuration | T-F/T-SELECT | Source/proxy review; R-AI same-origin requests | None/local | PASS | No credential or git-history investigation; no comprehensive secret-scan claim |
| §§11/15/17/logging | Do not log API keys or sensitive prompt/business data by default | 14–15 | I-AI logs raw throwable | T-AI synthetic marker appears in fresh logs | A17-01 reproduction, no provider call | Provider-neutral | FAIL | Raw exception content is not redacted; caller-only tests miss this |
| §17/diagnostics | Health/startup/Flyway/MCP development diagnostics | 1–4 | Actuator, Flyway, MCP | T-B/T-MCP | R-COMPOSE/R-DB/R-PROTOCOL | None | PASS | Sensitive logging is a separate failing row |
| §16/automated | Service, PostgreSQL, MCP, REST and frontend tests pass | 1–16 | Existing suites | T-B 285; T-F 59 | No skipped required tests | None/scripted | PASS | Does not substitute for live providers |
| §16/manual/local | Inspector, real MCP host, local application Ollama | 8–9/14/16 | I-MCP/I-AI | T-MCP/T-AI | H-P1 provenance plus fresh R-AI | Ollama | PASS — LOCAL OLLAMA | Inspector/host evidence retained from accepted Phase 1, not newly rerun |
| §16/manual/hosted | Validate at least one real hosted mode | 15 | I-CONFIG/I-AI | T-AI/T-SELECT only | H-HOLD | Hosted | BLOCKED — HOSTED ACCEPTANCE ON HOLD | Missing successful hosted runtime workflow |
| §18 | License before public distribution | Phase-wide | README marks TBD | Not applicable | No distribution attempted | None | PASS | Conditional prerequisite remains; no public-distribution readiness claim |
| Scope / Phase 3 exclusions | No new capability, write/RAG/chat/STDIO/resources/prompts/Phase 3 | 17 | Documentation-only diff; existing capabilities | T-MCP/T-F | Hash comparison; R-PROTOCOL | None | PASS | No implementation or test changes |

### Documentation, deliveries and exit criteria

| Requirement / criterion | Concise requirement | Owning slice | Implementation | Automated tests | Runtime/manual evidence | Provider mode | Status | Limitation/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| §19/files | README, PRD, architecture, plan and test plan present | 10/17 | D | git diff --check | File/link review | None | PASS | Versioned PRD/plan filenames are the accepted Slice 10 deviation |
| §19/startup | Prerequisites and reproducible Docker/local commands | 1/12/14/16/17 | D | T-B/T-F/T-BUILD | Commands rerun: R-COMPOSE/R-UI/R-AI | Ollama | PASS — LOCAL OLLAMA | Requires prepared .env DB values and installed local model |
| §19/MCP | Inspector setup, architecture and tool inventory | 4–10/17 | D, I-MCP | T-MCP | H-P1 plus current R-PROTOCOL | None | PASS | Historical interoperability evidence labeled as such |
| §19/provider-config | Document local vs hosted configuration and limitation | 14–17 | D, I-CONFIG | T-SELECT | Source/default comparison; H-HOLD | Both documentation | PASS | Hosted instructions describe existing knobs, not demonstrated hosted success; no activation attempted |
| §19/tests/limitations | Test commands and accurate known limitations | 10–17 | D, A17-01/blocked matrix | T-B/T-F | Actual gate results and missing evidence recorded | All | PASS | Does not conceal failed logging criterion |
| §21/deliveries | Angular, REST, shared service, visual screens, tests, local docs | 11–14/16 | I-UI/I-REST/I-CORE/I-AI/D | T-B/T-F | R-UI/R-PROTOCOL/R-AI | None/local | PASS — LOCAL OLLAMA | Hosted delivery acceptance split below |
| §21/visual-search | Business user visually searches real catalog | 12–13 | I-UI | T-F | R-UI | None | PASS | — |
| §21/workflow/local | Defined workflow works with local configured model | 14/16 | I-AI/I-ASSIST | T-AI/T-F | R-AI/R-DB | Ollama | PASS — LOCAL OLLAMA | — |
| §21/workflow/both | Workflow demonstrated with Ollama and hosted model | 15–16 | I-AI/I-ASSIST | T-AI/T-F only for hosted | R-AI plus H-HOLD | Both | BLOCKED — HOSTED ACCEPTANCE ON HOLD | Missing hosted UI/tool/data evidence |
| §21/UI-MCP-consistency | UI and MCP share authoritative data | 11–13/16 | I-CORE/I-REST/I-MCP | T-REST | R-UI/R-PROTOCOL/R-DB/R-AI | None/local | PASS | No fabricated item records observed |
| §21/switching | Switching requires configuration, no catalog code edit | 15 | I-CONFIG | T-SELECT | Static selection review | Both design | PASS | No live hosted success claim |
| §21/existing-Ollama | Works with existing local installation | 14/16 | I-CONFIG | T-AI | R-AI | Ollama | PASS — LOCAL OLLAMA | Ollama intentionally outside Compose |
| §21/hosted-mode | Hosted mode works with documented config | 15 | I-CONFIG, D | T-AI/T-SELECT | H-HOLD | Hosted | BLOCKED — HOSTED ACCEPTANCE ON HOLD | Real successful hosted execution absent |
| §22/feature-tests-errors | Implemented behavior, relevant tests, error cases | 11–16 | I-UI/I-REST/I-AI | T-B/T-F | R-UI/R-PROTOCOL | None/local | PASS | Sensitive logging and hosted acceptance split out |
| §22/feature-Docker-boundaries-docs | Docker valid, docs updated, boundaries intact | 1–17 | I-RUN, D | T-B boundary tests, T-BUILD | R-COMPOSE and static review | None/local | PASS | — |
| §22/no-secrets-in-source | No secrets introduced by audit; backend-only configuration | 1/12/14–17 | Environment-backed config; documentation-only diff | T-F request tests | No credential values inspected or added | None | PASS | Not a historical credential audit; logging separately FAIL |
| §22/aggregate-feature-DoD | All applicable requirements satisfied | 14–17 | Above evidence | Passing tests do not check logs | A17-01 confirmed | Provider-neutral | FAIL | Sensitive-logging requirement unmet in addition to hosted blocker |
| §22/initial-project-DoD | Startup, PostgreSQL tools, Inspector, bounds, local ports, contracts | 1–10/17 | I-RUN/I-MCP/I-CORE/D | T-B retained Phase 1 coverage | H-P1, R-COMPOSE/R-DB/R-PROTOCOL | None | PASS | Initial accepted evidence remains authoritative; audit adds no new Phase 1 acceptance claim |
| §22/Phase2/local | Stakeholder sees same catalog and local AI demonstration | 12–14/16 | I-UI/I-ASSIST | T-F/T-AI | R-UI/R-AI | Ollama | PASS — LOCAL OLLAMA | — |
| §22/Phase2/both | AI demonstration usable with either real provider | 15–16 | I-CONFIG/I-ASSIST | Deterministic coverage only for hosted | H-HOLD | Both | BLOCKED — HOSTED ACCEPTANCE ON HOLD | Cannot infer hosted UI acceptance from local result |
| §23/A | MCP-compatible AI host catalog search | 9 | I-MCP/I-CORE | T-MCP | Slice 9 opencode/local model evidence; fresh R-PROTOCOL same records | Local MCP host | PASS — LOCAL OLLAMA | Prior host demonstration reused, not rerun |
| §23/B | Human SERVICE/active/maxPrice 200 matches MCP | 11–12 | I-UI/I-REST/I-CORE | T-REST/T-F | R-UI/R-PROTOCOL six records | None | PASS | — |
| §23/C | Local model requests use local Ollama without hosted dependency | 14/16 | I-CONFIG/I-AI | T-SELECT | R-AI | Ollama | PASS — LOCAL OLLAMA | Architecture/observed path, no broader privacy guarantee |
| §23/D | Real hosted operation via configuration and external credentials | 15 | I-CONFIG | T-SELECT/T-AI | H-HOLD | Hosted | BLOCKED — HOSTED ACCEPTANCE ON HOLD | No real successful hosted model/tool/result run |
| Plan §11 / Slice 17 gate | All checklist items and applicable requirements evidenced | 17 | This matrix and checklist | Local gates pass | Hosted missing; A17-01 | Both | FAIL | Full acceptance stopped; hosted-dependent rows remain BLOCKED separately |

## 5. Fresh execution evidence

### Tests/build/Compose

The full backend gate passed 285 tests with zero failures, errors or skips. This includes
all retained Phase 1 service/persistence/MCP coverage, the approved REST architecture-test
transition, REST/MCP equivalence and deterministic assistant/provider selection tests.
The full frontend gate passed 59 tests in four files. Production build: 285.05 kB initial
bundle (77.95 kB estimated transfer), no budget warnings.

Compose configuration and build/up --wait succeeded. Frontend, backend and PostgreSQL
all reported healthy. Aggregate health and readiness returned HTTP 200 / UP. Frontend
publication: `127.0.0.1:4200`; backend: `127.0.0.1:8080`; PostgreSQL host bindings: `{}`.
Frontend `/mcp` returned 404. Flyway history remained versions 1 and 2, both successful;
24 seed rows were present. No volume deletion was required or performed.

### REST/MCP/security

A scratch, read-only local HTTP audit performed MCP initialization, initialized
notification, discovery and calls, then deleted its successful session. Server identity:
`mcp-catalog-server`, version `0.1.0`; capabilities `logging` and `tools`; exactly
`search_catalog` and `get_catalog_item`. Search with SERVICE/active/maxPrice=200 and
detail ID 16 returned full JSON equal to the corresponding live REST results.
REST pageSize=101 returned 400; missing ID 99999 returned 404. Hostile Origin returned
403 and hostile Host returned 421. Automated T-MCP and T-REST also independently cover
these boundaries against PostgreSQL.

The first scratch-client attempt parsed SSE only when the body began with `event:` or
`data:`; it failed on a valid event with another leading SSE field. The scratch parser
was corrected to find `data:` lines and the same audit passed. This was audit tooling,
not an application regression; no repository code or test was modified.

### Browser and local Ollama

Both existing browser scripts passed against the real Compose stack. Conventional smoke
covered default/paged/filtered catalog, empty/validation states, known/inactive/missing
detail, direct refresh, browser history, restored filters, offline retry and mobile layout.

```text
prompt: Show me active service items under $200.
POST /api/v1/catalog/assistant -> HTTP 200
provider: ollama
model: qwen3-coder-next:latest
capability: search_catalog
arguments: {"type":"SERVICE","active":true,"maxPrice":200}
page: 0; pageSize: 20; totalItems: 6; totalPages: 1
```

| ID | SKU | Price | Type | Active |
| --- | --- | --- | --- | --- |
| 13 | SVC-101 | 149.00 | SERVICE | true |
| 14 | SVC-102 | 85.00 | SERVICE | true |
| 15 | SVC-103 | 45.00 | SERVICE | true |
| 16 | SVC-104 | 199.00 | SERVICE | true |
| 19 | SVC-107 | 120.00 | SERVICE | true |
| 20 | SVC-108 | 0.00 | SERVICE | true |

Independent SQL confirmed every row above. The model chose inclusive maxPrice=200;
there are no seed rows priced exactly 200, so the agreed strict-under-200 scenario has
the same expected IDs. The UI rendered the exact backend answer and those returned
records; navigation to ID 16 and back worked. No frontend interpretation or generated
catalog record was needed. Catalog facts in response items come from the recorder's
capability output; this is not a guarantee that every future model summary is correct.

Playwright observed application requests only to the frontend origin, including the
relative REST request through the existing frontend proxy. No browser request directly
targeted `/mcp`, Ollama, or a hosted-provider endpoint; no browser runtime errors occurred.
No horizontal overflow at 390 px. Screenshots and JSON were captured as local `/tmp`
artifacts listed below, not added to the repository. Backend runtime metadata reported
only `ollama` / `qwen3-coder-next:latest`. Static wiring selects only the Ollama ChatModel
in this mode and has no fallback; no provider configuration was changed by this audit.

## 6. Exact commands and artifacts

Commands run from repository root unless a `cd` is shown. Long output was redirected to
the listed `/tmp/slice17-*.log` artifacts. No logs or environment dumps containing real
credentials were requested. The protected-file baseline and scratch protocol client are
local audit tools, not product or Python reference implementation files.

```bash
mvn -f backend/pom.xml --batch-mode --no-transfer-progress clean verify > /tmp/slice17-backend.log 2>&1
export PATH=/tmp/node-v22.22.3-linux-x64/bin:$PATH
cd frontend
npm test > /tmp/slice17-frontend-tests.log 2>&1 && npm run build > /tmp/slice17-frontend-build.log 2>&1
cd ..
AI_PROVIDER=ollama OLLAMA_MODEL=qwen3-coder-next:latest docker compose config --quiet
AI_PROVIDER=ollama OLLAMA_MODEL=qwen3-coder-next:latest docker compose up --build --wait --wait-timeout 300 > /tmp/slice17-compose.log 2>&1
docker compose ps
curl --fail --silent http://127.0.0.1:8080/actuator/health
curl --fail --silent http://127.0.0.1:8080/actuator/health/readiness
docker compose exec -T mcp-catalog-server printenv AI_PROVIDER OLLAMA_MODEL
docker compose port frontend 8080
docker compose port mcp-catalog-server 8080
docker inspect mcp-catalog-server-postgres-1 --format '{{json .HostConfig.PortBindings}}'
curl --silent --output /dev/null --write-out 'Frontend /mcp: %{http_code}\n' http://127.0.0.1:4200/mcp
python3 /tmp/slice17-protocol-audit.py > /tmp/slice17-protocol.log 2>&1
cd frontend
CHROME_BIN=/usr/bin/google-chrome npm run smoke > /tmp/slice17-browser.log 2>&1
CHROME_BIN=/usr/bin/google-chrome SMOKE_ASSISTANT_SCREENSHOT=/tmp/slice17-assistant.png SMOKE_ASSISTANT_MOBILE_SCREENSHOT=/tmp/slice17-assistant-mobile.png SMOKE_ASSISTANT_EVIDENCE=/tmp/slice17-assistant-evidence.json node scripts/catalog-assistant-smoke.mjs > /tmp/slice17-assistant.log 2>&1
cd ..
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT id, sku, type, active, price FROM public.catalog_item WHERE type = '\''SERVICE'\'' AND active = true AND price <= 200 ORDER BY id;" -c "SELECT version, success FROM public.flyway_schema_history ORDER BY installed_rank;" -c "SELECT count(*) FROM public.catalog_item;"' > /tmp/slice17-postgres.log
git diff --check
```

The scratch protocol client used the same requests documented in README's MCP section:
initialize (`protocolVersion=2025-06-18`), retain `Mcp-Session-Id`, notify initialized,
list tools, call search with `{type:SERVICE,active:true,maxPrice:200}`, call detail with
`{id:16}`, compare decoded tool text JSON to REST, then test hostile Origin/Host. The
reproducible repository-owned regression for these assertions is the full Maven command
above (`CatalogRestIntegrationTest`, transport/wiring/PhaseOne suites). No scratch script
is required for the project's normal startup/test commands.

Exact synthetic-marker check used after the full Maven run (read-only, no provider):

```python
from pathlib import Path
s = Path('/tmp/slice17-backend.log').read_text()
for marker in ['quota exceeded for key sk-secret-value',
               'Incorrect API key provided (Bearer sk-secret-value)',
               'raw provider payload sk-secret']:
    print(marker in s)  # True, True, True
```

## 7. Hosted-dependent criteria and missing evidence

All of the following are **BLOCKED — HOSTED ACCEPTANCE ON HOLD**:

- FR-11 hosted execution: missing successful real provider interpretation/native tool invocation and grounded response under delivered configuration.
- FR-13 both-provider workflow and D9 hosted UI: missing real browser → backend → hosted model → capability → PostgreSQL execution with response/provider/model/arguments/IDs/SKUs and rendered result evidence.
- §16 manual hosted compatibility: missing successful real hosted mode demonstration; scripted model/configuration tests are insufficient.
- §21 hosted workflow delivery and both-provider workflow exit criterion: missing the hosted half of the same demonstrated catalog scenario.
- §21 hosted-mode exit criterion: missing successful runtime evidence using the documented hosted configuration.
- §22 Phase 2 both-provider demonstration: missing the hosted stakeholder UI evidence.
- §23 Scenario D: implementation/configuration evidence exists, but successful real hosted operation is absent.
- Plan §11 FR-11/FR-13 checklist and Slice 17's both-provider gate: cannot close until that evidence exists.

H-HOLD records the external entitlement reason. No attempts were made to diagnose it,
inspect credentials, select another provider/model, change configuration or contact any
hosted provider. These blocks are distinct from A17-01, which is a genuine failed criterion.

## 8. Documentation corrections and exact files changed

Created `docs/SLICE_17_VALIDATION.md`.

Updated only current documentation:

1. `README.md` — current audit status, supported-provider vs unsupported-provider wording, server-side configuration reference without activation, logging caveat and audit link.
2. `frontend/README.md` — stale AI-deferred/40-test statements corrected to implemented assistant/59 tests; existing local smoke command and audit status.
3. `docs/ARCHITECTURE.md` — audit status; delivered hosted model default corrected from stale qwen-plus to qwen3.8-max by comparison to existing application.yml; "never logged" guarantee corrected with A17-01. No provider behavior changed.
4. `docs/TEST_PLAN.md` — current audit evidence, stale deferred-AI wording, and explicit test coverage gap for log output.
5. `docs/MCP_Catalog_Platform_IMPLEMENTATION_PLAN_v0.2.md` — Slice 15/16/17 status table, stale D8-open wording, local-audit authorization, evidence-backed checklist with hosted items blocked and logging item FAIL.

The preexisting `docs/SLICE_16_VALIDATION.md` diff remains present but unchanged by this
task. Original v0.1 plan and all historical validation files remain unchanged from the
task-start snapshot. No Angular/backend implementation, tests, dependencies/lockfiles,
Docker/Compose, provider/security configuration, contracts or migrations were modified.

## 9. Final disposition

**Slice 17 local audit activities: complete. Local functional Ollama Phase 2 evidence:
passing. Full Slice 17 acceptance: not complete — A17-01 FAIL and hosted-dependent
criteria BLOCKED. Phase 2 both-provider acceptance: OUTSTANDING.**

Slice 16 remains COMPLETE for local Ollama acceptance; Slice 15 hosted acceptance remains
ON HOLD. No hosted-provider acceptance claim. No failed criterion was waived. No Phase 3
work was started. No commit was made. `git diff --check` passed; protected-file hash
comparison confirmed documentation-only changes and preserved historical evidence.
