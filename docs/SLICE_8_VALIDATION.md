# Slice 8 validation

Executed 2026-09-23. **Slice 8 passed; Slice 9 has not been started.**

Slice 8 validates the running Dockerized Phase 1 server with **MCP Inspector 2.7.0**:
a real browser session against the Inspector web UI plus Inspector's own CLI mode. Every
required check was performed by Inspector; no substitute client was used.

## 1. Production code changes

**None.** No production source, configuration, migration, dependency or Docker file was
changed. The only temporary artifact was an out-of-repository Compose override in `/tmp`
used to *observe* Origin behaviour, and it was removed before the final gates:

```yaml
# /tmp/insp-override.yml  (temporary, deleted after the observation; never in the repo)
services:
  mcp-catalog-server:
    environment:
      MCP_SERVER_SECURITY_ALLOWED_ORIGINS: http://deny.invalid
```

No Inspector interoperability defect was found, so the production-change rule was not
triggered. The default Origin/Host allowlists were **not** broadened for Inspector.

## Baseline verified before any Inspector work

- Repository at commit `4f9d1fd` plus the Slice 7 working-tree changes; `git status` clean
  of stray files.
- `mvn clean verify` → **184 tests, 0 failures** (identical to the Slice 7 baseline).
- `docker compose up --build --wait` → both services healthy; `Registered tools: 2`.
- Backend published on `127.0.0.1:8080` only; PostgreSQL `5432/tcp` `null` (unpublished).
- Actuator aggregate health and readiness both `UP`.

## 2. MCP Inspector version

`@modelcontextprotocol/inspector` **2.7.0** (npm `latest` for the package on
2026-09-23; the package reports `v2.7.0` in its UI footer). Invoked through `npx`,
which installed it into the npx cache. Node v24.18.0, npm 12.1.0.

## 3. Launch commands

```bash
# Mode discovery
npx -y @modelcontextprotocol/inspector@2.7.0 --help
npx -y @modelcontextprotocol/inspector@2.7.0 --cli --help
npx -y @modelcontextprotocol/inspector@2.7.0 --web --help

# Web UI (browser path) - started in the background
npx -y @modelcontextprotocol/inspector@2.7.0 --web \
  --transport http --server-url http://127.0.0.1:8080/mcp
#   -> MCP Inspector Web is up and running at:
#      http://127.0.0.1:6274?MCP_INSPECTOR_API_TOKEN=<token>
#      Sandbox (MCP Apps): http://127.0.0.1:6275/sandbox

# CLI mode (non-interactive path) - one connection per invocation
npx -y @modelcontextprotocol/inspector@2.7.0 --cli \
  --server-url http://127.0.0.1:8080/mcp --transport http --format json --method <method>
```

The web UI was driven with Google Chrome (`/usr/bin/google-chrome`, headless) over the
Chrome DevTools Protocol to perform a genuine browser session: connect, list tools,
inspect schemas, edit arguments in the Ace JSON editor and execute the tools.

## 4. Transport and endpoint

```text
Transport: HTTP (Inspector label: "Streamable HTTP")
Endpoint:  http://127.0.0.1:8080/mcp
Protocol:  MCP 2025-11-25 (negotiated by Inspector; server also offers 2024-11-05,
           2025-03-26 and 2025-06-18)
```

Inspector sent 3 HTTP POSTs for the connection handshake/listing (observed in its
Network tab: 200, 202, 200).

## 5. Origin behaviour observed

| Observation | Result |
| --- | --- |
| Inspector web UI (browser) with the **default** allowlist | Connects (status `Connected`, MCP 2025-11-25) |
| Inspector CLI with the **default** allowlist | Connects |
| Inspector sending `Origin: http://localhost:6274` explicitly | **Accepted** — matches the default `http://localhost:*` entry |
| Inspector sending `Origin: http://127.0.0.1:6274` explicitly | Accepted — matches the default `http://127.0.0.1:*` entry |
| Inspector sending `Origin: http://evil.example` | Rejected: HTTP **403** `Invalid Origin header` |
| Inspector (no explicit header) against a **deny-`localhost:6274`** allowlist | Still connects → **Inspector sends no `Origin` header at all** |
| Inspector web UI (browser) against the same deny-`localhost:6274` allowlist | Still connects → the browser path is proxy-mediated too |
| Inspector sending `Origin: http://localhost:6274` against the deny allowlist | Rejected: HTTP **403** `Invalid Origin header` |

**Conclusion:** the Slice 4 browser-Origin concern does **not** materialise for MCP
Inspector 2.7.0. The Inspector web UI talks to a local Inspector proxy, which performs
the MCP HTTP requests, so no `Origin` header reaches the MCP server from the browser.
Even so, a hypothetical client sending `Origin: http://localhost:6274` would already be
permitted by the default loopback allowlist, so **no allowlist change is required for
Inspector**. Origin validation remains genuinely enforced and was proven by making
Inspector send an Origin the allowlist does not contain.

Note: the Network tab in the Inspector UI lists request URL/status/timing but does not
expose request headers, so the Origin determination above was made by controlled
allowlist experiments through Inspector rather than by reading its UI.

Minor Inspector-side observation, **not a server defect**: for the 403 the CLI reported
`{"error":{"code":"auth_required",...,"status":403}}`. The server returned the correct
`403 Invalid Origin header`; Inspector's error taxonomy maps any 403 to its
authorization bucket.

## 6. Temporary allowlist configuration

None was needed for Inspector to work. The temporary deny-list override described in
section 1 was used **only to observe** whether Inspector sends an Origin, and:

- the default server rejected `Origin: http://localhost:6274` while the override was
  active (proving the allowlist mechanism is enforced, not ignored);
- Inspector connected with the override in place (proving it sends no Origin);
- the override file was deleted and the stack restarted from the unmodified Compose
  file;
- the default posture was re-verified afterwards: absent Origin → 400 (accepted),
  `http://localhost:6274` → 400 (accepted), `http://evil.example` → 403, `Host:
  evil.example` → 421.

## 7. Initialize / server identity (Inspector)

Inspector CLI `--method initialize`:

```json
{"protocolVersion":"2025-11-25",
 "serverInfo":{"name":"mcp-catalog-server","version":"0.1.0"},
 "capabilities":{"logging":{},"tools":{"listChanged":true}}}
```

The Inspector web UI header shows `mcp-catalog-server` and `Connected (…ms)` with the
negotiated protocol `MCP 2025-11-25`. Identity matches the required
`mcp-catalog-server` / `0.1.0`.

## 8. Capabilities (Inspector)

Advertised: `logging` and `tools` only. Not advertised: `resources`, `prompts`,
`completions`. `resources/list` and `prompts/list` return empty arrays
(`{"resources":[]}`, `{"prompts":[]}`) and `resources/templates/list` returns
`{"resourceTemplates":[]}` — the MCP SDK answers those list methods with empty results,
but **nothing is registered and nothing is advertised**. This is unchanged Slice 4/5
behaviour, not a Phase 3 feature.

## 9. tools/list (Inspector)

Web UI Tools page and CLI both show exactly two tools:

```text
get_catalog_item
search_catalog
```

## 10. search_catalog via Inspector

- **Schema inspected (web UI):** all six parameters displayed with their descriptions
  (`type`, `active`, `maxPrice`, `text`, `page`, `pageSize`); CLI `tools/list` returned
  the identical `inputSchema` (`required: []`, `additionalProperties: false`).
- **Success invocation:**
  `{"type":"SERVICE","active":true,"maxPrice":200,"page":0,"pageSize":20}` →
  `totalItems = 6`, `totalPages = 1`, ids `[13,14,15,16,19,20]`; items `SVC-101`,
  `SVC-102`, `SVC-103`, `SVC-104`, `SVC-107`, `SVC-108` with prices `149.00`, `85.00`,
  `45.00`, `199.00`, `120.00`, `0.00`. The Inspector UI renders prices as `149`, `199`
  etc. because it re-parses the JSON numbers for display; the wire text carries the
  two-decimal form.
- **Validation failure:** `{"pageSize":101}` → **Tool Error**,
  `Page size must be between 1 and 100`.

## 11. get_catalog_item via Inspector

- **Schema inspected (web UI):** a single required `id` (integer) with its description;
  CLI returned the identical schema (`required: ["id"]`).
- **Success invocation:** `{"id":16}` → `{"id":16,"sku":"SVC-104","name":"Network Health
  Assessment","type":"SERVICE","description":"Review office network configuration and
  provide a prioritized findings report.","price":199.00,"active":true,
  "createdAt":"2026-01-15T09:00:00Z","updatedAt":"2026-01-15T09:00:00Z"}` — exactly the
  seeded row.
- **Not found:** `{"id":99999}` → **Tool Error**, `Catalog item not found: 99999`.

## 12. Validation / not-found error results

| Case | Inspector surface | Text |
| --- | --- | --- |
| search `pageSize=101` | Tool Error (UI), `isError: true` (CLI) | `Page size must be between 1 and 100` |
| detail `id=99999` | Tool Error (UI), `isError: true` (CLI) | `Catalog item not found: 99999` |

Both are useful, non-fabricating and free of stack traces, SQL, paths or credentials.

## 13. Security regression results

Verified after restoring the default configuration:

| Check | Result |
| --- | --- |
| absent `Origin` (non-browser clients) | HTTP 400 — accepted, body rejected |
| `Origin: http://localhost:6274` | HTTP 400 — accepted by the loopback wildcard |
| `Origin: http://evil.example` | HTTP **403** `Invalid Origin header` |
| `Host: evil.example` | HTTP **421** `Invalid Host header` |
| GET `/mcp` | HTTP 400 — route present, Streamable HTTP |
| backend publication | `127.0.0.1:8080` only |
| PostgreSQL publication | `5432/tcp` = `null` (unpublished) |

The default allowlist was never modified; the temporary deny-list override was removed
and the default posture re-proven.

## 14. Maven / test results

`mvn clean verify`: **BUILD SUCCESS — 184 tests, 0 failures, 0 errors, 0 skips** (both
before and after the Inspector work; the test suites are unchanged from Slice 7).

## 15. Docker / health / data integrity results

- `docker compose up --build --wait` passed; backend and PostgreSQL both healthy.
- Aggregate health HTTP 200 `{"groups":["liveness","readiness"],"status":"UP"}`;
  readiness HTTP 200 `{"status":"UP"}`.
- Live inventory: exactly `['get_catalog_item', 'search_catalog']`.
- `flyway_schema_history`: V1 checksum `162354501`, V2 checksum `-2124124441`, both
  `success=t`; no migration added or modified.
- `catalog_item` row count 24 (unchanged seed data).

## 16. Deviations from PRD/plan

- **No deviation.** Slice 8 is operational validation; production code, configuration,
  dependencies, migrations and contracts are untouched. The temporary origin override
  lived outside the repository and was removed.
- The Slice 4 documentation said a browser Inspector origin "needs an explicit allowlist
  entry; confirming that is Slice 8 work". Slice 8 confirms the opposite for Inspector
  2.7.0: the browser path is proxy-mediated and sends no `Origin`, and
  `http://localhost:6274` would be permitted by the default loopback allowlist anyway.
  `docs/ARCHITECTURE.md` was updated with this finding; it is a factual correction of an
  outstanding note, not a new architecture decision.

## 17. Unresolved risks / questions

1. **Inspector version sensitivity.** The Origin finding is specific to MCP Inspector
   2.7.0's proxy architecture. A future Inspector version that issues MCP requests
   directly from the browser would send `Origin: http://localhost:6274`, which the
   default allowlist already permits, so the practical risk stays low — but this has not
   been tested against other Inspector versions.
2. **Inspector error taxonomy.** Inspector classifies the Origin 403 as `auth_required`.
   Any future automated check that inspects Inspector's error `code` rather than the HTTP
   status could misread a security rejection; the server's status and message remain
   correct.
3. **UI price rendering.** Inspector's UI re-parses JSON numbers, so `199.00` displays as
   `199`. This is an Inspector display behaviour, not a server contract change; the wire
   format keeps two decimals.
4. **Slice 9 outstanding.** No real MCP host (for example Claude Code) has been validated
   yet; only Inspector and the Java SDK client have exercised the server.
5. Inherited risks unchanged: SDK-owned schema-validation wording, no
   `outputSchema`/`structuredContent`, and the Slice 4 transport-bean override tracking
   Spring AI upgrades.

No rollback was needed and no production defect was found. **Slice 9 remains unstarted.**
