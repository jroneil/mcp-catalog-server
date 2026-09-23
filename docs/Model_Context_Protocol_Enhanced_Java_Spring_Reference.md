# Model Context Protocol (MCP) — Enhanced Java & Spring Reference

*A practical, Java-first guide to understanding MCP, building MCP clients and servers, and applying the protocol in Spring Boot projects.*

**Enhanced edition:** September 21, 2026  
**Focus:** Java, Spring Boot, Spring AI, MCP server design, testing, and production boundaries

---

## 0. Purpose of This Guide

The Model Context Protocol (MCP) is an open standard for connecting AI applications to external tools, data, and reusable prompts through a consistent protocol.

This guide is intentionally **Java/Spring-first**. Python examples are retained because Python remains useful for quick prototypes and because many MCP examples in the ecosystem use it, but the primary implementation path here is:

```text
AI Host
  ↓
MCP Client
  ↓
MCP Protocol / JSON-RPC 2.0
  ↓
Spring Boot MCP Server
  ↓
Existing Application Service
  ↓
Repository / API / Database
```

The goal is not to build another chatbot. The goal is to learn how to expose useful application capabilities through a standard protocol without coupling business logic to an AI model or vendor.

---

# 1. What MCP Actually Is

The Model Context Protocol is an open standard, originally developed by Anthropic, for connecting AI applications (**hosts**) to external **tools**, **resources**, and **prompts** through a consistent interface.

A useful analogy is **USB-C for AI applications**: instead of writing a custom integration for every model/application/data-source pairing, you expose a capability once through MCP and compatible clients can discover and use it.

## 1.1 The three roles

### Host
The AI application the user interacts with.

Examples:

- Claude Desktop
- Claude Code
- an IDE with MCP support
- a custom Spring AI application
- an agent framework

The host usually owns the model interaction and one or more MCP clients.

### Client
The protocol participant inside the host that maintains a connection to a particular MCP server.

A useful rule of thumb:

```text
one MCP client connection ↔ one MCP server
```

A host may manage many client/server connections simultaneously.

### Server
The application that exposes capabilities through MCP.

A server can expose:

- **Tools** — executable operations
- **Resources** — readable context/data
- **Prompts** — reusable prompt templates
- optionally additional protocol features such as completion, logging, progress, sampling, and elicitation

## 1.2 The simplest mental model

```text
Tool     ≈ function/action
Resource ≈ readable data/GET
Prompt   ≈ reusable prompt template
```

That simplification is not the whole specification, but it is an excellent working model while learning.

## 1.3 MCP is not the model

MCP does **not** replace an LLM API.

It provides a standard way for an AI host to discover and interact with capabilities.

```text
LLM                     decides what it wants to do
MCP                     standardizes how capabilities are exposed/called
Your application code   performs the actual business work
```

This separation is one of MCP's most important architectural properties.

---

# 2. Protocol Fundamentals

MCP uses **JSON-RPC 2.0** messages over a transport.

At a high level, a connection looks like this:

```text
Client                         Server
  │                              │
  │──── initialize ─────────────>│
  │<─── capabilities/info ───────│
  │                              │
  │──── tools/list ─────────────>│
  │<─── tool definitions ────────│
  │                              │
  │──── tools/call ─────────────>│
  │<─── result/error ────────────│
  │                              │
```

The same general pattern exists for resources and prompts.

## 2.1 Initialization and capability negotiation

During initialization, the client and server exchange protocol and capability information.

This matters because an MCP implementation should not assume every peer supports every optional capability.

A server should advertise only what it actually implements.

For a first project, that may be only:

```text
tools = supported
resources = not yet supported
prompts = not yet supported
```

Then add capabilities deliberately as the project grows.

## 2.2 Discovery is part of the contract

MCP clients discover tools rather than relying on hardcoded knowledge.

A tool definition typically includes:

```text
name
human/model-readable description
input JSON Schema
optional output schema
annotations/hints
```

This means your Java types and descriptions are not merely internal implementation details. They become part of the contract a model sees.

---

# 3. Tools, Resources, and Prompts

## 3.1 Tools

Tools perform work.

Examples:

```text
search_catalog
get_catalog_item
create_work_order
lookup_customer
find_class_usages
```

Tools may be read-only or mutating.

A strong design principle is:

> MCP tools should be thin adapters over existing application services.

Prefer:

```text
@McpTool
      ↓
CatalogService
      ↓
CatalogRepository
```

Avoid:

```text
@McpTool
  ├── SQL
  ├── validation rules
  ├── pricing logic
  ├── authorization logic
  └── unrelated orchestration
```

The MCP layer should expose capabilities, not become a second business layer.

## 3.2 Tool schemas are APIs

A model cannot reliably call a vague interface.

Bad:

```text
search(query: String)
```

Better:

```text
searchCatalog(
    itemType,
    activeOnly,
    maxPrice,
    text
)
```

The model can reason about explicit fields more reliably than parsing an undocumented mini-language inside a single string.

### Good tool-design rules

- use stable, descriptive names
- keep arguments small and typed
- prefer enums where the domain is constrained
- clearly mark optional versus required fields
- explain units and formats
- return structured results
- make errors actionable
- do not silently reinterpret invalid input

## 3.3 Tool descriptions matter

Tool descriptions are read by the client/model.

Write them as if they were a concise API description for an intelligent caller.

Weak:

```text
Search catalog
```

Better:

```text
Search catalog items using optional item type, active status,
maximum price, and text filters. Returns matching catalog items
without modifying catalog data.
```

## 3.4 Resources

Resources expose readable context using URIs or URI templates.

Example:

```text
catalog://items/123
catalog://categories/service
legacy://modules/order-entry
```

Use a resource when the main purpose is **retrieving context**, not performing an action.

For example:

```text
Tool:
search_catalog(filters)

Resource:
catalog://items/{id}
```

That distinction can keep an MCP surface clean and understandable.

## 3.5 Prompts

Prompts expose reusable prompt templates through the protocol.

Examples:

```text
catalog-analysis
legacy-module-summary
customer-history-review
```

Prompts are useful when a repeatable reasoning template belongs with the server/domain rather than being hardcoded independently into every client.

For an initial MCP learning project, tools are enough. Add resources and prompts only after the first tool works end to end.

---

# 4. Transports

The transport moves JSON-RPC messages between client and server.

## 4.1 stdio

The server runs as a local subprocess and communicates over standard input/output.

Good fit for:

- local developer tools
- desktop integrations
- Claude Desktop / local host use cases
- tightly controlled machine-local execution

Conceptually:

```text
Host
  └── launches server process
        stdin  → requests
        stdout ← responses
```

### Important stdio rule

Do not write arbitrary application logging to stdout if stdout is carrying protocol messages. Send logs to stderr or use the SDK's logging mechanisms.

## 4.2 Streamable HTTP

Streamable HTTP is the current preferred transport for new network-accessible MCP services.

Good fit for:

- Spring Boot services
- remote servers
- container deployments
- enterprise infrastructure
- horizontally deployed services

For a Java/Spring learning project, Streamable HTTP is an excellent first remote transport because it maps naturally to infrastructure you already understand.

## 4.3 SSE

The older HTTP+SSE transport remains relevant for compatibility with older clients and SDK versions, but current MCP SDK direction favors Streamable HTTP for new development.

For new work:

```text
local-only server       → stdio
remote/networked server → Streamable HTTP
legacy compatibility    → SSE only when required
```

---

# 5. Java MCP SDK

**Repository:** `modelcontextprotocol/java-sdk`  
**Maven group:** `io.modelcontextprotocol.sdk`

As of September 2026, the active Java SDK line is **2.0.x**. The 2.0 line tracks the MCP 2025-11-25 specification. The 1.1.x and 0.18.x lines remain security-patch lines rather than the primary development line.

Always verify the current release before starting a new project because the MCP SDKs continue to evolve rapidly.

## 5.1 Dependency management

A BOM is the cleanest way to keep Java SDK modules aligned.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.modelcontextprotocol.sdk</groupId>
            <artifactId>mcp-bom</artifactId>
            <version>2.0.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then add the modules appropriate for the project.

Do not copy a version from an old tutorial without checking the current Java SDK release line.

## 5.2 Sync versus async

The Java SDK supports both blocking and reactive programming models.

### Synchronous

```text
McpSyncServer
McpSyncClient
```

Best first choice when:

- operations are straightforward
- traffic is modest
- your service layer is blocking
- simplicity matters more than theoretical throughput

### Asynchronous/reactive

```text
McpAsyncServer
McpAsyncClient
```

Consider when:

- the application is already Reactor/WebFlux based
- operations spend substantial time waiting on network I/O
- concurrency requirements justify the complexity

Do not choose reactive simply because MCP supports it. Match the programming model to the application underneath it.

---

# 6. Spring AI's MCP Layer

Spring AI builds on top of the Java MCP SDK and provides Boot starters plus annotation-based handlers.

For an existing Spring shop, this is often the most productive implementation layer.

## 6.1 Server annotations

Spring AI provides server-side annotations including:

```text
@McpTool
@McpResource
@McpPrompt
@McpComplete
```

The annotation layer can automatically generate tool parameter JSON schemas and register annotated Spring beans with an MCP server.

## 6.2 A basic tool

```java
@Component
public class CatalogTools {

    private final CatalogService catalogService;

    public CatalogTools(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @McpTool(
        name = "search_catalog",
        description = "Search catalog items using optional item type, active status, maximum price, and text filters"
    )
    public List<CatalogItemResponse> searchCatalog(
            @McpToolParam(description = "Catalog item type", required = false)
            String itemType,

            @McpToolParam(description = "Maximum item price", required = false)
            BigDecimal maxPrice,

            @McpToolParam(description = "When true, return only active items", required = false)
            Boolean activeOnly) {

        return catalogService.search(itemType, maxPrice, activeOnly);
    }
}
```

The important architecture is not the annotation. It is the boundary:

```text
MCP adapter
    ↓
application service
    ↓
domain/repository/integration
```

## 6.3 Tool hints

Modern Spring AI MCP annotations also support hints such as:

```text
readOnlyHint
destructiveHint
idempotentHint
openWorldHint
```

These are useful metadata for clients, but treat them as **hints**, not security controls.

Authorization must still be enforced server-side.

## 6.4 Resources

```java
@McpResource(
    uri = "catalog://items/{id}",
    name = "catalog-item",
    description = "Returns one catalog item by ID",
    mimeType = "application/json"
)
public String getCatalogItem(String id) {
    // Delegate to application service.
}
```

## 6.5 Prompts

```java
@McpPrompt(
    name = "catalog-analysis",
    description = "Create a structured analysis prompt for a catalog item"
)
public GetPromptResult catalogAnalysis(...) {
    // Build reusable prompt messages.
}
```

Prompts are useful, but do not add them merely to demonstrate every MCP primitive. Add them when they have an actual domain purpose.

---

# 7. Recommended Track 2 Skeleton Project

Use one deliberately small project whose purpose is learning the protocol cleanly.

Suggested repository:

```text
mcp-catalog-server/
├── pom.xml
├── README.md
├── docs/
│   ├── PRD.md
│   ├── ARCHITECTURE.md
│   ├── IMPLEMENTATION_PLAN.md
│   └── TEST_PLAN.md
│
├── src/main/java/com/example/mcpcatalog/
│   ├── McpCatalogApplication.java
│   │
│   ├── mcp/
│   │   ├── CatalogTools.java
│   │   ├── CatalogResources.java
│   │   └── CatalogPrompts.java
│   │
│   ├── catalog/
│   │   ├── CatalogService.java
│   │   ├── CatalogRepository.java
│   │   ├── CatalogItem.java
│   │   └── CatalogItemResponse.java
│   │
│   └── config/
│       └── McpConfig.java
│
├── src/main/resources/
│   ├── application.yml
│   └── data.sql
│
└── src/test/java/com/example/mcpcatalog/
    ├── CatalogServiceTest.java
    ├── CatalogToolsTest.java
    └── McpIntegrationTest.java
```

## 7.1 Minimal domain model

Keep the catalog intentionally small:

```text
CatalogItem
-----------
id
sku
name
type
description
price
active
```

Seed perhaps 15–25 believable records.

Do not import a large existing application at first. The learning question should remain:

```text
Do I understand MCP and its contracts?
```

not:

```text
Can I simultaneously integrate a complex existing business system?
```

## 7.2 First vertical slice

**Given** catalog data exists  
**When** an MCP client calls `search_catalog` with:

```json
{
  "itemType": "SERVICE",
  "maxPrice": 200,
  "activeOnly": true
}
```

**Then** the server returns only matching catalog items.

Nothing else is required for milestone 1.

---

# 8. A Micro-Slice Implementation Plan

## Slice 0 — Docs and boundaries

Create:

```text
PRD.md
ARCHITECTURE.md
IMPLEMENTATION_PLAN.md
TEST_PLAN.md
```

Decide explicitly:

- Java version
- Spring Boot/Spring AI versions
- sync or async
- first transport
- first tool contract
- persistence choice
- what is intentionally out of scope

### Exit gate

The architecture can be explained in one diagram and the first tool contract is written before implementation begins.

---

## Slice 1 — Domain without MCP

Implement:

```text
CatalogItem
CatalogRepository
CatalogService
```

Test ordinary catalog search behavior first.

### Exit gate

Service tests pass without MCP involved.

This proves business behavior independently of the protocol adapter.

---

## Slice 2 — First MCP tool

Expose:

```text
search_catalog
```

No resources, prompts, LLMs, RAG, or agent framework yet.

### Exit gate

A protocol-level client can discover the tool and invoke it successfully.

---

## Slice 3 — MCP Inspector

Use MCP Inspector to verify:

- server initialization
- advertised capabilities
- tool discovery
- generated input schema
- valid calls
- invalid calls
- error responses

### Exit gate

The tool can be tested interactively without relying on an LLM.

This is important because it separates **protocol correctness** from **model behavior**.

---

## Slice 4 — Real host integration

Connect a real MCP host.

Examples:

```text
Claude Desktop
Claude Code
compatible IDE
custom Spring AI host
```

Ask naturally phrased questions such as:

```text
Find active service items under $200.
```

Observe the actual arguments chosen by the model.

### Exit gate

The host discovers and successfully calls the tool using natural language.

---

## Slice 5 — Second tool

Add:

```text
get_catalog_item
```

This gives you a useful contrast between broad search and precise lookup.

### Exit gate

Tool names and schemas are unambiguous enough that the model chooses the appropriate operation reliably.

---

## Slice 6 — Resource

Add:

```text
catalog://items/{id}
```

Compare tool lookup versus resource retrieval.

### Exit gate

You can explain when a capability should be a tool versus a resource.

---

## Slice 7 — Prompt

Add one genuinely useful prompt, for example:

```text
catalog-analysis
```

Do not add prompts purely for completeness.

### Exit gate

The prompt has a clear business reason to exist independently of a specific host.

---

## Slice 8 — Minimal Java client

Write a small client against your own server.

Learn:

- initialization
- tool listing
- tool invocation
- error handling
- transport configuration

### Exit gate

You understand MCP from both server and client sides.

---

## Slice 9 — Second transport

If the project began with Streamable HTTP, add stdio, or vice versa.

### Exit gate

The same business capability works without redesigning the service layer.

That proves the transport boundary is clean.

---

## Slice 10 — Security and production hardening

Only after the protocol works:

- authentication
- authorization
- request size limits
- timeouts
- origin validation where relevant
- audit logging
- rate limiting where appropriate
- sensitive-data review
- deployment configuration

### Exit gate

The MCP server can be treated as a real application integration surface rather than a local demo.

---

# 9. Testing Strategy

MCP systems should be tested in layers.

## 9.1 Service tests

Test business rules without MCP.

```text
CatalogServiceTest
```

Examples:

- filters active items correctly
- applies maximum price correctly
- handles missing filters
- validates invalid ranges

## 9.2 Adapter/tool tests

Test the MCP-facing Java method.

```text
CatalogToolsTest
```

Verify:

- arguments map correctly to the service
- outputs are structured and stable
- domain errors become appropriate tool errors

## 9.3 Protocol integration tests

Run an actual MCP client against the server.

Verify:

```text
initialize
list tools
call tool
bad arguments
unknown tool
server error
```

## 9.4 Inspector tests

Use MCP Inspector while developing because it exposes the protocol surface directly.

This should happen before model-driven testing.

## 9.5 Host/model tests

Finally, test natural-language behavior.

Example prompts:

```text
Find active services under $200.
Show me item SKU SVC-104.
Are there any inactive items cheaper than $50?
```

At this layer, you are testing whether your descriptions and schemas are understandable to a model—not whether the underlying business logic works.

---

# 10. Error Design

A useful MCP server should fail predictably.

Avoid returning vague strings such as:

```text
Something went wrong
```

Prefer errors that identify the category and actionable cause.

Examples:

```text
CATALOG_ITEM_NOT_FOUND
INVALID_PRICE_RANGE
INVALID_ITEM_TYPE
UPSTREAM_TIMEOUT
NOT_AUTHORIZED
```

Do not leak:

- stack traces
- secrets
- database connection details
- internal filesystem paths
- unrelated customer data

Model-facing errors should be informative enough for recovery but not reveal internal implementation details unnecessarily.

---

# 11. Security Model

Treat an MCP server as an API surface.

The fact that a model is calling it does not make the request trusted.

## 11.1 Authorization belongs in the server

Never rely on:

- tool descriptions
- client hints
- the LLM's judgment
- prompt instructions

for authorization.

If the current user may not access a record, the server must reject the operation regardless of what the model requested.

## 11.2 Separate read from write tools

Early projects should favor read-only tools.

When write operations are introduced, make their effects explicit.

For example:

```text
search_catalog       read-only
get_catalog_item     read-only
create_catalog_item  mutating
archive_catalog_item mutating
```

Tool metadata such as `readOnlyHint`, `destructiveHint`, and `idempotentHint` can help clients reason about operations, but they are not enforcement mechanisms.

## 11.3 Human approval

For consequential operations, a good architecture is:

```text
Model proposes action
      ↓
Human reviews/approves
      ↓
Server performs action
```

This is especially appropriate for:

- financial operations
- deleting records
- sending communications
- changing permissions
- production deployment actions

## 11.4 Input validation

Treat model-provided arguments like any external API input.

Validate:

- ranges
- IDs
- enum values
- maximum lengths
- dates
- URLs
- file paths
- tenant/customer scope

Never assume structured JSON means trusted input.

---

# 12. Observability

At minimum, capture:

```text
request/correlation ID
tool name
duration
success/failure
error category
caller identity when available
```

Avoid logging entire arguments/results by default when they may contain sensitive business data.

Useful metrics include:

```text
tool calls per tool
error rate
latency p50/p95/p99
timeouts
upstream failures
rejected authorization attempts
```

Keep model behavior and tool execution observability separate when possible. A poor model decision and a server bug are different failure classes.

---

# 13. What Not to Do

## Do not make the MCP layer your business layer

MCP should call services that already enforce business rules.

## Do not start with ten tools

One correct tool teaches more than a large generated surface you do not understand.

## Do not begin with an LLM as your test harness

Use service tests, protocol tests, and MCP Inspector first.

## Do not expose raw CRUD automatically

An MCP surface should represent useful domain capabilities, not simply mirror every repository method.

## Do not make schemas vague

The schema and description are what the client/model sees.

## Do not introduce RAG just because the project involves AI

MCP and RAG solve different problems.

```text
MCP → standard capability integration
RAG → retrieval of relevant knowledge/context
```

They can work together later, but neither requires the other.

## Do not make the model responsible for security

The server owns authorization and validation.

---

# 14. Python SDK Reference

Python remains useful for rapid MCP prototypes and for understanding examples from the broader ecosystem.

**Repository:** `modelcontextprotocol/python-sdk`  
**Package:** `mcp`

Because the Python SDK has gone through major-version transition work, check the package/repository before pinning a version.

## 14.1 FastMCP server

```python
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("Demo")

@mcp.tool()
def add(a: int, b: int) -> int:
    """Add two numbers."""
    return a + b

@mcp.resource("file:///example.txt")
def read_example() -> str:
    return "This is example content"

if __name__ == "__main__":
    mcp.run()
```

FastMCP is a high-level API that handles schema generation, lifecycle, and transport wiring.

## 14.2 Python design notes

- type hints contribute to generated schemas
- docstrings influence tool descriptions
- stdio remains a natural local transport
- Streamable HTTP is appropriate for remote services

The concepts transfer directly to Java even though the framework syntax differs.

---

# 15. Java vs. Python

| Area | Python | Java/Spring |
|---|---|---|
| Fastest tiny prototype | Excellent | Good |
| Boilerplate | Low | Moderate |
| Type safety | Good with hints/tooling | Strong compile-time model |
| Enterprise Spring integration | Limited | Excellent |
| Existing JVM service reuse | Weak fit | Excellent |
| MCP example volume | Very high | Increasing rapidly |
| Blocking model | Available | First-class |
| Reactive model | asyncio | Reactor / async SDK |
| Best use | scripts, prototypes, data tooling | enterprise services, existing Spring apps |

For a developer already working primarily in Spring Boot, there is no strong reason to prototype every MCP project in Python first.

A better rule is:

```text
If the target system is Spring/Java → start in Java.
If the task is a disposable script/prototype → Python may be faster.
```

---

# 16. Recommended Java-First Learning Path

## Step 1 — Understand the protocol before the framework

Be able to explain:

```text
host
client
server
JSON-RPC
capability negotiation
tool
resource
prompt
transport
```

Do not begin by memorizing annotations.

## Step 2 — Build one useful server tool

Use Java/Spring and expose one real operation.

Recommended first tool:

```text
search_catalog
```

## Step 3 — Inspect the protocol directly

Use MCP Inspector.

Understand what the client actually sees:

- server information
- capability declarations
- tool descriptions
- JSON schemas
- results
- errors

## Step 4 — Connect a real host

Use Claude Desktop, Claude Code, or another compatible host.

Observe how natural language is translated into your structured tool arguments.

## Step 5 — Add one more tool

Prefer a complementary capability such as:

```text
get_catalog_item
```

## Step 6 — Add a resource

Expose one URI-addressable piece of read-only context.

## Step 7 — Add a prompt only if useful

Do not treat prompts as a required checkbox.

## Step 8 — Write a Java client

Call your own server without a model.

This makes transport and lifecycle behavior concrete.

## Step 9 — Learn the second transport

Know both stdio and Streamable HTTP.

## Step 10 — Harden it

Learn:

- auth
- authorization
- timeouts
- limits
- safe errors
- auditability
- deployment

Only then treat the project as a reusable production pattern.

---

# 17. Suggested Track 2 Projects

## Project A — Catalog Search MCP Server

Expose product/catalog search to an MCP-compatible host.

Tools:

```text
search_catalog
get_catalog_item
```

Resource:

```text
catalog://items/{id}
```

Why it is useful: it mirrors a realistic post-modernization client request—letting an AI assistant query an existing business application through a controlled service surface.

## Project B — Legacy Codebase MCP Server

Expose reverse-engineering capabilities for a legacy application.

Possible tools:

```text
find_class_usages
summarize_module
list_database_tables_touched
find_routes_for_controller
```

The MCP layer should call deterministic analysis/indexing code where possible rather than asking an LLM to rediscover everything on every request.

## Project C — Personal Ops MCP Server

A low-risk server around notes/tasks/calendar-like data.

This is a useful place to experiment with:

- resources
- prompts
- multiple tools
- stdio
- client behavior

without production or customer consequences.

---

# 18. Definition of Done for the First MCP Project

The first project is complete when all of these are true:

```text
[ ] Spring Boot application starts cleanly
[ ] MCP initialization succeeds
[ ] server advertises only intended capabilities
[ ] search_catalog appears in tools/list
[ ] input schema is understandable and constrained
[ ] MCP Inspector can call the tool
[ ] invalid input returns a deliberate error
[ ] service logic is separately unit-tested
[ ] a real MCP host can discover and call the tool
[ ] logs do not expose sensitive data
[ ] README explains how to run and test the server
[ ] architecture keeps MCP separate from business logic
```

Anything beyond that is a second milestone.

---

# 19. Glossary

**Host** — application managing the user/model experience and MCP client connections.

**Client** — MCP protocol participant that connects a host to one server.

**Server** — exposes MCP capabilities.

**Tool** — callable operation exposed by a server.

**Resource** — URI-addressable readable context exposed by a server.

**Prompt** — reusable prompt template exposed by a server.

**JSON-RPC 2.0** — message protocol MCP uses for requests, responses, and notifications.

**Transport** — mechanism carrying MCP messages, such as stdio or Streamable HTTP.

**Capability negotiation** — initialization process by which participants declare supported protocol features.

**Sampling** — MCP capability allowing a server to request model generation through the client/host under supported configurations.

**Elicitation** — capability for a server to request additional user-provided information through the client.

**MCP Inspector** — development/testing utility for directly interacting with MCP servers.

---

# 20. References

## Protocol

- MCP documentation and specification: https://modelcontextprotocol.io/

## Java

- MCP Java SDK: https://github.com/modelcontextprotocol/java-sdk
- MCP Java SDK releases: https://github.com/modelcontextprotocol/java-sdk/releases

## Spring AI

- Spring AI MCP overview: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html
- Spring AI MCP server annotations: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-server.html
- Spring AI MCP security: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-security.html

## Python

- MCP Python SDK: https://github.com/modelcontextprotocol/python-sdk

## Examples

- MCP example servers: https://github.com/modelcontextprotocol/servers

---

# 21. Current-Version Notes — September 2026

This ecosystem changes quickly. Before starting a new implementation, verify:

```text
MCP specification revision
Java SDK release line
Spring AI version
transport support in the intended host/client
security guidance
```

At the time this edition was prepared:

- the Java SDK **2.0.x** line is the active development line
- Java SDK 2.0 tracks the **2025-11-25 MCP specification**
- Streamable HTTP is favored over the older SSE transport for new networked work
- Spring AI provides annotation-based MCP tools, resources, prompts, and client/server integration

Do not allow an old tutorial's dependency versions to become part of a new project's architecture by accident.

---

## Final Working Rule

Start with one useful capability and understand every layer it crosses:

```text
Natural-language request
        ↓
Host/model chooses tool
        ↓
MCP client
        ↓
JSON-RPC / transport
        ↓
MCP server adapter
        ↓
Spring application service
        ↓
Repository/API/database
        ↓
Structured result
```

Once that path is clear, the rest of MCP becomes incremental rather than mysterious.
