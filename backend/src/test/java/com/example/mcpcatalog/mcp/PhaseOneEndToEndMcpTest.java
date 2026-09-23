package com.example.mcpcatalog.mcp;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcSseServerTransportProvider;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.ai.util.JsonHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 7 end-to-end acceptance suite for the complete Phase 1 MCP path:
 *
 * <pre>
 * MCP client -&gt; Streamable HTTP /mcp -&gt; transport security -&gt; tool discovery
 *   -&gt; search_catalog / get_catalog_item -&gt; CatalogService -&gt; repository -&gt; PostgreSQL
 * </pre>
 *
 * <p>Every scenario is driven by a real MCP Java SDK client over the real HTTP
 * transport against seeded PostgreSQL; nothing is stubbed.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PhaseOneEndToEndMcpTest {

	private static final String SEARCH = "search_catalog";

	private static final String DETAIL = "get_catalog_item";

	private static final String SEARCH_SCHEMA = """
			{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","additionalProperties":false,
			 "properties":{
			   "type":{"type":"string","description":"Optional item type filter. Must be exactly PRODUCT or SERVICE. Omit to include both types."},
			   "active":{"type":"boolean","description":"Optional active-status filter. true returns only active items, false returns only inactive items. Omit to include both."},
			   "maxPrice":{"type":"number","description":"Optional inclusive maximum price, for example 200 or 199.99. Must be at least 0, at most 9999999999.99, and have no more than two decimal places. Omit for no price limit."},
			   "text":{"type":"string","description":"Optional literal text matched case-insensitively anywhere in the SKU, name or description. At most 200 characters. Omit for no text filter."},
			   "page":{"type":"integer","description":"Optional zero-based page number. Defaults to 0. Must be between 0 and 10000."},
			   "pageSize":{"type":"integer","description":"Optional number of items per page. Defaults to 20. Must be between 1 and 100."}},
			 "required":[]}
			""";

	private static final String DETAIL_SCHEMA = """
			{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","additionalProperties":false,
			 "properties":{
			   "id":{"type":"integer","description":"Catalog item identifier. Must be a positive integer, for example 16. Use search_catalog to discover identifiers."}},
			 "required":["id"]}
			""";

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-trixie");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("DB_URL", postgres::getJdbcUrl);
		registry.add("DB_USERNAME", postgres::getUsername);
		registry.add("DB_PASSWORD", postgres::getPassword);
	}

	@LocalServerPort
	int port;

	@Autowired
	McpSyncServer mcpSyncServer;

	@Autowired
	WebMvcStreamableServerTransportProvider streamableTransportProvider;

	@Autowired
	ApplicationContext applicationContext;

	@Autowired
	Environment environment;

	@Autowired
	com.example.mcpcatalog.mcp.config.McpTransportSecurityProperties securityProperties;

	private McpSyncClient client;

	@BeforeEach
	void connectAndInitialize() {
		this.client = McpClient
			.sync(HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + this.port)
				.endpoint("/mcp")
				.build())
			.clientInfo(new McpSchema.Implementation("slice7-e2e-client", "0.1.0"))
			.requestTimeout(Duration.ofSeconds(20))
			.build();
		this.client.initialize();
	}

	@AfterEach
	void disconnect() {
		if (this.client != null) {
			this.client.close();
		}
	}

	// ---------------------------------------------------------------- discovery

	@Test
	void initializeSucceedsAndAdvertisesOnlyTheToolCapability() {
		assertThat(this.client.isInitialized()).isTrue();

		McpSchema.Implementation serverInfo = this.client.getServerInfo();
		assertThat(serverInfo.name()).isEqualTo("mcp-catalog-server");
		assertThat(serverInfo.version()).isEqualTo("0.1.0");

		McpSchema.ServerCapabilities capabilities = this.client.getServerCapabilities();
		assertThat(capabilities.tools()).isNotNull();
		assertThat(capabilities.resources()).isNull();
		assertThat(capabilities.prompts()).isNull();
		assertThat(capabilities.completions()).isNull();
	}

	@Test
	void toolsListReturnsExactlyTheTwoAcceptedProductionTools() {
		assertThat(this.client.listTools().tools()).extracting(McpSchema.Tool::name)
			.containsExactlyInAnyOrder(SEARCH, DETAIL);
		assertThat(this.mcpSyncServer.listTools()).extracting(McpSchema.Tool::name)
			.containsExactlyInAnyOrder(SEARCH, DETAIL);
	}

	@Test
	void inputSchemasAreUnchangedFromTheAcceptedSliceFiveAndSixContracts() {
		assertThat(findTool(SEARCH).inputSchema())
			.isEqualTo(new JsonHelper().fromJsonToMap(SEARCH_SCHEMA));
		assertThat(findTool(DETAIL).inputSchema())
			.isEqualTo(new JsonHelper().fromJsonToMap(DETAIL_SCHEMA));
		assertThat(findTool(SEARCH).description()).contains("catalog", "PRODUCT", "SERVICE", "pageSize");
		assertThat(findTool(DETAIL).description()).contains("catalog item", "identifier", "error");
	}

	// ----------------------------------------------------------- search_catalog

	@Test
	void noArgumentsReturnsTheDefaultFirstPageInIdAscendingOrder() {
		Map<String, Object> payload = payload(call(SEARCH, Map.of()));

		assertThat(payload).containsEntry("page", 0).containsEntry("pageSize", 20);
		assertThat(asLong(payload, "totalItems")).isEqualTo(24);
		assertThat(asLong(payload, "totalPages")).isEqualTo(2);
		assertThat(items(payload)).hasSize(20);
		assertThat(ids(payload)).isSorted().startsWith(1L, 2L, 3L);
	}

	@Test
	void productAndServiceTypeFiltersReturnOnlyTheirOwnType() {
		Map<String, Object> products = payload(call(SEARCH, Map.of("type", "PRODUCT", "pageSize", 100)));
		Map<String, Object> services = payload(call(SEARCH, Map.of("type", "SERVICE", "pageSize", 100)));

		assertThat(asLong(products, "totalItems")).isEqualTo(12);
		assertThat(items(products)).allSatisfy(item -> assertThat(item).containsEntry("type", "PRODUCT"));
		assertThat(asLong(services, "totalItems")).isEqualTo(12);
		assertThat(items(services)).allSatisfy(item -> assertThat(item).containsEntry("type", "SERVICE"));
	}

	@Test
	void activeFilterSelectsActiveAndInactiveItems() {
		Map<String, Object> active = payload(call(SEARCH, Map.of("active", true, "pageSize", 100)));
		Map<String, Object> inactive = payload(call(SEARCH, Map.of("active", false, "pageSize", 100)));

		assertThat(asLong(active, "totalItems")).isEqualTo(18);
		assertThat(items(active)).allSatisfy(item -> assertThat(item).containsEntry("active", true));
		assertThat(asLong(inactive, "totalItems")).isEqualTo(6);
		assertThat(items(inactive)).allSatisfy(item -> assertThat(item).containsEntry("active", false));
	}

	@Test
	void maximumPriceFilterIsInclusive() {
		Map<String, Object> payload = payload(call(SEARCH, Map.of("maxPrice", new BigDecimal("199.00"), "pageSize", 100)));

		assertThat(asLong(payload, "totalItems")).isEqualTo(15);
		assertThat(items(payload)).allSatisfy(
				item -> assertThat(((Number) item.get("price")).doubleValue()).isLessThanOrEqualTo(199.00));
		assertThat(ids(payload)).contains(5L, 16L);
	}

	@Test
	void textSearchIsCaseInsensitiveAndLiteral() {
		Map<String, Object> lower = payload(call(SEARCH, Map.of("text", "network", "pageSize", 100)));
		Map<String, Object> upper = payload(call(SEARCH, Map.of("text", "NETWORK", "pageSize", 100)));

		assertThat(asLong(lower, "totalItems")).isEqualTo(4);
		assertThat(ids(lower)).containsExactly(2L, 6L, 16L, 19L);
		assertThat(ids(upper)).isEqualTo(ids(lower));
		assertThat(items(lower)).allSatisfy(item -> assertThat(
				(item.get("sku") + " " + item.get("name") + " " + item.get("description")).toLowerCase(java.util.Locale.ROOT))
			.contains("network"));
	}

	@Test
	void combinedFiltersReturnOnlyMatchingRows() {
		Map<String, Object> payload = payload(call(SEARCH,
				Map.of("type", "SERVICE", "active", true, "maxPrice", new BigDecimal("200"), "page", 0, "pageSize", 20)));

		assertThat(asLong(payload, "totalItems")).isEqualTo(6);
		assertThat(asLong(payload, "totalPages")).isEqualTo(1);
		assertThat(ids(payload)).containsExactly(13L, 14L, 15L, 16L, 19L, 20L);
	}

	@Test
	void maximumPageSizeIsAcceptedAndOutOfRangePageKeepsTotals() {
		Map<String, Object> full = payload(call(SEARCH, Map.of("pageSize", 100)));
		Map<String, Object> beyond = payload(call(SEARCH, Map.of("page", 5, "pageSize", 20)));

		assertThat(full).containsEntry("pageSize", 100);
		assertThat(items(full)).hasSize(24);
		assertThat(beyond).containsEntry("page", 5);
		assertThat(items(beyond)).isEmpty();
		assertThat(asLong(beyond, "totalItems")).isEqualTo(24);
		assertThat(asLong(beyond, "totalPages")).isEqualTo(2);
	}

	@Test
	void orderingIsDeterministicAcrossIdenticalCalls() {
		List<Long> first = ids(payload(call(SEARCH, Map.of("pageSize", 100))));
		List<Long> second = ids(payload(call(SEARCH, Map.of("pageSize", 100))));

		assertThat(first).isEqualTo(second).isSorted();
		assertThat(first).hasSize(24).startsWith(1L).endsWith(24L);
	}

	@Test
	void invalidCriteriaReturnTheIntendedValidationMessages() {
		assertThat(errorText(call(SEARCH, Map.of("pageSize", 101))))
			.isEqualTo("Page size must be between 1 and 100");
		assertThat(errorText(call(SEARCH, Map.of("page", -1)))).isEqualTo("Page must be between 0 and 10000");
		assertThat(errorText(call(SEARCH, Map.of("maxPrice", new BigDecimal("-1")))))
			.isEqualTo("Maximum price must be between 0 and 9999999999.99 with at most two decimal places");
		assertThat(errorText(call(SEARCH, Map.of("type", "FOO")))).isEqualTo("Type must be PRODUCT or SERVICE");
		assertThat(errorText(call(SEARCH, Map.of("text", "a".repeat(201)))))
			.isEqualTo("Text must contain at most 200 characters and no NUL character");
	}

	@Test
	void noUnboundedResultSetCanBeRequested() {
		assertThat(errorText(call(SEARCH, Map.of("pageSize", 1_000_000))))
			.isEqualTo("Page size must be between 1 and 100");
		assertThat(items(payload(call(SEARCH, Map.of("pageSize", 100)))).size()).isLessThanOrEqualTo(100);
	}

	@Test
	void resultsMatchTheSeededPostgresRowsExactly() {
		Map<String, Object> item = items(payload(call(SEARCH, Map.of("text", "SVC-104")))).get(0);

		assertThat(item).containsEntry("id", 16)
			.containsEntry("sku", "SVC-104")
			.containsEntry("name", "Network Health Assessment")
			.containsEntry("type", "SERVICE")
			.containsEntry("active", true)
			.containsEntry("createdAt", "2026-01-15T09:00:00Z")
			.containsEntry("updatedAt", "2026-01-15T09:00:00Z");
		assertThat((String) item.get("description"))
			.isEqualTo("Review office network configuration and provide a prioritized findings report.");
		assertThat(((Number) item.get("price")).doubleValue()).isEqualTo(199.00);
	}

	// -------------------------------------------------------- get_catalog_item

	@Test
	void knownActiveItemIsReturnedExactlyAsStored() {
		Map<String, Object> item = payload(call(DETAIL, Map.of("id", 16)));

		assertThat(item).containsEntry("id", 16)
			.containsEntry("sku", "SVC-104")
			.containsEntry("name", "Network Health Assessment")
			.containsEntry("type", "SERVICE")
			.containsEntry("active", true)
			.containsEntry("createdAt", "2026-01-15T09:00:00Z")
			.containsEntry("updatedAt", "2026-01-15T09:00:00Z");
		assertThat(((Number) item.get("price")).doubleValue()).isEqualTo(199.00);
	}

	@Test
	void knownInactiveItemIsRetrievableByIdentifier() {
		Map<String, Object> item = payload(call(DETAIL, Map.of("id", 22)));

		assertThat(item).containsEntry("id", 22)
			.containsEntry("sku", "SVC-110")
			.containsEntry("name", "Legacy Email Account Setup")
			.containsEntry("type", "SERVICE")
			.containsEntry("active", false);
		assertThat(((Number) item.get("price")).doubleValue()).isEqualTo(35.00);
	}

	@Test
	void detailResultMatchesTheSearchRepresentationForTheSameRow() {
		Map<String, Object> detail = payload(call(DETAIL, Map.of("id", 16)));
		List<Map<String, Object>> searched = items(payload(call(SEARCH, Map.of("text", "SVC-104"))));

		assertThat(searched).hasSize(1);
		assertThat(detail).isEqualTo(searched.get(0));
	}

	@Test
	void unknownIdentifierReturnsTheDefinedNotFoundError() {
		McpSchema.CallToolResult result = call(DETAIL, Map.of("id", 99999));

		assertThat(result.isError()).isEqualTo(true);
		assertThat(errorText(result)).isEqualTo("Catalog item not found: 99999");
		assertThat(errorText(result)).doesNotStartWith("{");
		assertNoInternalDetails(errorText(result));
	}

	@Test
	void nonPositiveIdentifiersAreRejectedWithTheIntendedMessage() {
		assertThat(errorText(call(DETAIL, Map.of("id", 0)))).isEqualTo("Catalog item ID must be positive");
		assertThat(errorText(call(DETAIL, Map.of("id", -5)))).isEqualTo("Catalog item ID must be positive");
	}

	@Test
	void missingAndWrongTypedIdentifiersAreRejectedWithoutInternalDetails() {
		McpSchema.CallToolResult missing = call(DETAIL, Map.of());
		McpSchema.CallToolResult wrongType = call(DETAIL, Map.of("id", "not-a-number"));

		assertThat(missing.isError()).isEqualTo(true);
		assertThat(wrongType.isError()).isEqualTo(true);
		assertThat(errorText(missing)).isNotBlank();
		assertThat(errorText(wrongType)).isNotBlank();
		assertNoInternalDetails(errorText(missing));
		assertNoInternalDetails(errorText(wrongType));
	}

	// ------------------------------------------------------ security/transport

	@Test
	void validAndAbsentOriginsAreAcceptedButInvalidOriginsAndHostsAreRejected() throws Exception {
		HttpResponse<String> loopbackOrigin = postMcp("http://127.0.0.1:" + this.port, "not-json");
		assertThat(loopbackOrigin.statusCode()).as("loopback origin must pass").isEqualTo(400);
		assertThat(loopbackOrigin.body()).contains("Invalid message format");

		HttpResponse<String> absentOrigin = postMcp(null, "not-json");
		assertThat(absentOrigin.statusCode()).as("non-browser clients send no Origin").isEqualTo(400);

		HttpResponse<String> crossOrigin = postMcp("http://evil.example", "not-json");
		assertThat(crossOrigin.statusCode()).isEqualTo(403);
		assertThat(crossOrigin.body()).isEqualTo("Invalid Origin header");

		String wrongHost = rawPostWithHost("evil.example:8080");
		assertThat(wrongHost).startsWith("HTTP/1.1 421").contains("Invalid Host header");

		String loopbackHost = rawPostWithHost("127.0.0.1:" + this.port);
		assertThat(loopbackHost).startsWith("HTTP/1.1 400").contains("Invalid message format");
	}

	@Test
	void mcpEndpointRemainsRegisteredAsStreamableHttpOnly() throws Exception {
		HttpResponse<String> get = getMcp();
		assertThat(get.statusCode()).as("registered route, not 404").isEqualTo(400);
		assertThat(get.body()).contains("Accept");

		assertThat(this.streamableTransportProvider).isNotNull();
		assertThat(this.environment.getProperty("spring.ai.mcp.server.protocol")).isEqualTo("STREAMABLE");
		assertThat(this.environment.getProperty("spring.ai.mcp.server.type")).isEqualTo("SYNC");
		assertThat(this.environment.getProperty("spring.ai.mcp.server.stdio", Boolean.class)).isFalse();
		assertThat(this.applicationContext.getBeanNamesForType(StdioServerTransportProvider.class)).isEmpty();
		assertThat(this.applicationContext.getBeanNamesForType(WebMvcSseServerTransportProvider.class)).isEmpty();
	}

	@Test
	void serverRemainsConfiguredForLoopbackOnly() {
		assertThat(this.environment.getProperty("server.address")).isEqualTo("127.0.0.1");
		assertThat(this.securityProperties.getAllowedOrigins())
			.containsExactly("http://127.0.0.1:*", "http://localhost:*");
		assertThat(this.securityProperties.getAllowedHosts()).containsExactly("127.0.0.1:*", "localhost:*");
	}

	// ------------------------------------------------------------------ helpers

	private McpSchema.Tool findTool(String name) {
		return this.client.listTools()
			.tools()
			.stream()
			.filter(tool -> tool.name().equals(name))
			.findFirst()
			.orElseThrow();
	}

	private McpSchema.CallToolResult call(String tool, Map<String, Object> arguments) {
		return this.client.callTool(McpSchema.CallToolRequest.builder(tool)
			.arguments(new LinkedHashMap<>(arguments))
			.build());
	}

	private static Map<String, Object> payload(McpSchema.CallToolResult result) {
		assertThat(result.isError()).as("expected success but got: %s", singleTextBlock(result)).isNotEqualTo(true);
		return new JsonHelper().fromJsonToMap(singleTextBlock(result));
	}

	private static String errorText(McpSchema.CallToolResult result) {
		assertThat(result.isError()).isEqualTo(true);
		return singleTextBlock(result);
	}

	private static String singleTextBlock(McpSchema.CallToolResult result) {
		assertThat(result.content()).hasSize(1);
		return ((McpSchema.TextContent) result.content().get(0)).text();
	}

	private static void assertNoInternalDetails(String text) {
		assertThat(text).doesNotContain("Exception", "java.", "at com.", "org.springframework", "jdbc:", "password",
				"SELECT ", ".java:");
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> items(Map<String, Object> payload) {
		return (List<Map<String, Object>>) payload.get("items");
	}

	private static List<Long> ids(Map<String, Object> payload) {
		return items(payload).stream().map(item -> ((Number) item.get("id")).longValue()).toList();
	}

	private static long asLong(Map<String, Object> payload, String key) {
		return ((Number) payload.get(key)).longValue();
	}

	private HttpResponse<String> getMcp() throws Exception {
		try (HttpClient httpClient = HttpClient.newHttpClient()) {
			return httpClient.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.port + "/mcp")).GET().build(),
					HttpResponse.BodyHandlers.ofString());
		}
	}

	private HttpResponse<String> postMcp(String origin, String body) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.port + "/mcp"))
			.header("Content-Type", "application/json")
			.header("Accept", "application/json, text/event-stream")
			.POST(HttpRequest.BodyPublishers.ofString(body));
		if (origin != null) {
			request.header("Origin", origin);
		}
		try (HttpClient httpClient = HttpClient.newHttpClient()) {
			return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}
	}

	/** The JDK HTTP client cannot set a synthetic {@code Host} header. */
	private String rawPostWithHost(String hostHeader) throws IOException {
		byte[] body = "not-json".getBytes(StandardCharsets.UTF_8);
		try (Socket socket = new Socket("127.0.0.1", this.port)) {
			String requestHead = "POST /mcp HTTP/1.1\r\n" + "Host: " + hostHeader + "\r\n"
					+ "Content-Type: application/json\r\n" + "Accept: application/json, text/event-stream\r\n"
					+ "Content-Length: " + body.length + "\r\n" + "Connection: close\r\n\r\n";
			OutputStream out = socket.getOutputStream();
			out.write(requestHead.getBytes(StandardCharsets.US_ASCII));
			out.write(body);
			out.flush();
			return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}
