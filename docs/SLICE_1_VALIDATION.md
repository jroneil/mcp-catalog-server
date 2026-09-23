# Slice 1 validation

Executed 2026-09-23. Decision gate and Slice 1 complete; Slice 2 not started.

## Files created

- `docs/ARCHITECTURE.md`: version choices, compatibility sources, transport/package/test/host decisions.
- `backend/pom.xml`: Java 21 Maven build, pinned dependency management, MVC/Actuator/JDBC and test dependencies.
- `backend/src/main/java/com/example/mcpcatalog/McpCatalogApplication.java`: entry point.
- `backend/src/main/resources/application.yml`: externalized database settings, loopback host default, health/readiness.
- `backend/src/test/java/com/example/mcpcatalog/McpCatalogApplicationTest.java`: real PostgreSQL context/JDBC/HTTP test.
- `backend/Dockerfile`, `backend/.dockerignore`: multi-stage non-root runtime and health check.
- `docker-compose.yml`: database readiness dependency, persistent volume, private PostgreSQL and loopback HTTP.
- `.env.example`, `.gitignore`, `README.md`: local setup and exclusions.
- `docs/SLICE_1_VALIDATION.md`: this record.

A local ignored `.env` was generated with random credentials for verification; its values are not reproduced here. Build outputs are ignored. Original input documents were not edited. This workspace has no Git metadata, so no commit or Git diff was possible.

## Commands and outcomes

| Command | Result |
| --- | --- |
| `java -version` | Host Java 21.0.12.1 |
| `mvn -version` | Host Maven 3.9.12; Docker build uses pinned 3.9.16 |
| `docker version` / `docker compose version` | Engine 29.6.1 / Compose 5.3.1 |
| `mvn -f backend/pom.xml --batch-mode --no-transfer-progress clean verify` | PASS: compilation, test compilation, one context test, packaging; zero failures/errors/skips |
| `docker build -t mcp-catalog-server:0.1.0 backend` | PASS |
| `docker compose config --quiet` | PASS |
| `docker compose config --format json` with a redacted projection | PASS: host IP 127.0.0.1; PostgreSQL has no ports; dependency is service_healthy |
| `docker compose up --build --wait --wait-timeout 180` | PASS: final source rebuilt; fresh volume initialized; both services healthy |
| `docker compose ps` | Backend and PostgreSQL healthy |
| `curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health` | HTTP 200, `{"groups":["liveness","readiness"],"status":"UP"}` |
| `curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/readiness` | HTTP 200, `{"status":"UP"}` |
| `docker compose port mcp-catalog-server 8080` | `127.0.0.1:8080` |
| `docker inspect $(docker compose ps -q) --format '{{.Name}} health={{.State.Health.Status}} ports={{json .NetworkSettings.Ports}}'` | Both healthy; only binding is backend `8080/tcp` to HostIp `127.0.0.1`, HostPort `8080`; PostgreSQL `5432/tcp` is null |
| `docker compose exec -T postgres sh -c 'pg_isready -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'` | Accepting connections |
| PostgreSQL `SELECT version()` / public table count via `psql` | PostgreSQL 18.6; zero public tables |
| `mvn -f backend/pom.xml --batch-mode --no-transfer-progress dependency:tree '-Dincludes=org.springframework.boot:*,org.postgresql:*,org.testcontainers:*,org.flywaydb:*,org.springframework.ai:*,io.modelcontextprotocol.sdk:*'` | Boot 4.1.1, JDBC 42.7.13, Testcontainers 2.0.5; no Flyway/AI/MCP runtime artifacts |

Initial test run failed solely because the test expected an exact aggregate health JSON body without the health-group metadata. The corrected assertion checks status and absence of exposed health details. Final Maven and Compose gates passed. Mockito emitted a Java 21 dynamic-agent warning during tests; no test was skipped.

Logs for this execution are in `/tmp/mcp-catalog-maven.log`, `/tmp/mcp-catalog-docker-build.log`, `/tmp/mcp-catalog-compose.log`, and `/tmp/mcp-catalog-dependencies.log` (ephemeral, not repository artifacts).

## Scope, deviations and remaining risks

- No implementation-plan scope deviation. `backend/` follows the task/plan rather than the PRD's illustrative `server-java/` layout.
- Configuration requires a one-time `.env` setup; no credentials are hard-coded to make an unconfigured checkout start. After that, the standard `docker compose up --build` path works.
- Flyway 12.4.0 and Spring AI 2.0.1 / MCP SDK 2.0.0 are pinned for later slices, not activated. There are no migrations, catalog classes, repositories, MCP endpoints/tools, frontend, model-provider integrations, resources, prompts, or STDIO.
- Testcontainers is mandatory and needs a usable Docker daemon. Docker image builds deliberately skip test execution; the separate host Maven gate executes it.
- Runtime image release tags are pinned but not immutable digests; upstream OS rebuilds can change image contents. No unresolved Boot/AI/MCP compatibility conflict was found.
- Origin handling, MCP protocol tests and Claude Code interoperability remain explicit future gates; they have not been claimed as validated here.
- All commands required an approved sandbox bypass because the environment's default shell sandbox failed with `mountinfo path is not absolute`. No automatic approval rejection occurred.
- Stack left running on loopback for review. Stop with `docker compose down`; retain the database volume unless deletion is intentional.
