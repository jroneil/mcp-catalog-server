package com.example.mcpcatalog.mcp;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.util.JsonHelper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 5 gate: a real MCP client drives the production {@code search_catalog}
 * tool over Streamable HTTP against the seeded PostgreSQL catalog.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SearchCatalogMcpIntegrationTest {

	private static final String TOOL = "search_catalog";

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

	private McpSyncClient client;

	@BeforeEach
	void connectAndInitialize() {
		this.client = McpClient
			.sync(HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + this.port)
				.endpoint("/mcp")
				.build())
			.clientInfo(new McpSchema.Implementation("slice5-test-client", "0.1.0"))
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

	@Test
	void searchCatalogIsDiscoverableWithTheDocumentedSchema() {
		McpSchema.ListToolsResult tools = this.client.listTools();

		assertThat(tools.tools()).extracting(McpSchema.Tool::name).containsExactly(TOOL);
		McpSchema.Tool tool = tools.tools().get(0);
		assertThat(tool.description()).contains("catalog", "PRODUCT", "SERVICE");

		Map<String, Object> schema = tool.inputSchema();
		assertThat(schema).containsEntry("type", "object").containsEntry("additionalProperties", false);
		assertThat((List<?>) schema.get("required")).isEmpty();
		assertThat(properties(schema)).containsOnlyKeys("type", "active", "maxPrice", "text", "page", "pageSize");
		assertThat(propertyType(schema, "type")).isEqualTo("string");
		assertThat(propertyType(schema, "active")).isEqualTo("boolean");
		assertThat(propertyType(schema, "maxPrice")).isEqualTo("number");
		assertThat(propertyType(schema, "text")).isEqualTo("string");
		assertThat(propertyType(schema, "page")).isEqualTo("integer");
		assertThat(propertyType(schema, "pageSize")).isEqualTo("integer");
	}

	@Test
	void noFilterRequestReturnsTheDefaultFirstPageFromPostgres() {
		Map<String, Object> payload = payload(call(Map.of()));

		assertThat(payload).containsEntry("page", 0).containsEntry("pageSize", 20);
		assertThat(asLong(payload, "totalItems")).isEqualTo(24);
		assertThat(asLong(payload, "totalPages")).isEqualTo(2);
		assertThat(items(payload)).hasSize(20);
		assertThat(ids(payload)).isSorted();
		assertThat(ids(payload).get(0)).isEqualTo(1L);
	}

	@Test
	void noFilterRequestAlsoWorksWhenTheArgumentsPayloadIsAbsent() {
		McpSchema.CallToolResult result = this.client.callTool(McpSchema.CallToolRequest.builder(TOOL)
			.arguments((Map<String, Object>) null)
			.build());

		Map<String, Object> payload = payload(result);

		assertThat(payload).containsEntry("page", 0).containsEntry("pageSize", 20);
		assertThat(asLong(payload, "totalItems")).isEqualTo(24);
		assertThat(items(payload)).hasSize(20);
	}

	@Test
	void productFilterReturnsOnlyProducts() {
		Map<String, Object> payload = payload(call(Map.of("type", "PRODUCT")));

		assertThat(asLong(payload, "totalItems")).isEqualTo(12);
		assertThat(items(payload)).allSatisfy(item -> assertThat(item).containsEntry("type", "PRODUCT"));
	}

	@Test
	void serviceFilterReturnsOnlyServices() {
		Map<String, Object> payload = payload(call(Map.of("type", "SERVICE")));

		assertThat(asLong(payload, "totalItems")).isEqualTo(12);
		assertThat(items(payload)).allSatisfy(item -> assertThat(item).containsEntry("type", "SERVICE"));
	}

	@Test
	void activeFilterSelectsOnlyActiveItems() {
		Map<String, Object> payload = payload(call(Map.of("active", true)));

		assertThat(asLong(payload, "totalItems")).isEqualTo(18);
		assertThat(items(payload)).allSatisfy(item -> assertThat(item).containsEntry("active", true));
	}

	@Test
	void activeFilterSelectsOnlyInactiveItems() {
		Map<String, Object> payload = payload(call(Map.of("active", false)));

		assertThat(asLong(payload, "totalItems")).isEqualTo(6);
		assertThat(items(payload)).allSatisfy(item -> assertThat(item).containsEntry("active", false));
	}

	@Test
	void maximumPriceFilterIsInclusive() {
		Map<String, Object> payload = payload(call(Map.of("maxPrice", new BigDecimal("199.00"), "pageSize", 100)));

		assertThat(asLong(payload, "totalItems")).isEqualTo(15);
		assertThat(items(payload)).allSatisfy(
				item -> assertThat(((Number) item.get("price")).doubleValue()).isLessThanOrEqualTo(199.00));
		assertThat(ids(payload)).contains(5L, 16L);
	}

	@Test
	void textSearchIsCaseInsensitiveAndLiteral() {
		Map<String, Object> lower = payload(call(Map.of("text", "network", "pageSize", 100)));
		Map<String, Object> upper = payload(call(Map.of("text", "NETWORK", "pageSize", 100)));
		Map<String, Object> mixed = payload(call(Map.of("text", "NeTwOrK", "pageSize", 100)));

		assertThat(asLong(lower, "totalItems")).isEqualTo(4);
		assertThat(ids(lower)).containsExactly(2L, 6L, 16L, 19L);
		assertThat(ids(upper)).isEqualTo(ids(lower));
		assertThat(ids(mixed)).isEqualTo(ids(lower));
		assertThat(items(lower)).allSatisfy(item -> assertThat(
				(item.get("sku") + " " + item.get("name") + " " + item.get("description")).toLowerCase(Locale.ROOT))
			.contains("network"));
	}

	@Test
	void combinedFiltersReturnOnlyMatchingRows() {
		Map<String, Object> payload = payload(
				call(Map.of("type", "SERVICE", "active", true, "maxPrice", new BigDecimal("200"), "page", 0,
						"pageSize", 20)));

		assertThat(asLong(payload, "totalItems")).isEqualTo(6);
		assertThat(asLong(payload, "totalPages")).isEqualTo(1);
		assertThat(ids(payload)).containsExactly(13L, 14L, 15L, 16L, 19L, 20L);
		assertThat(items(payload)).allSatisfy(item -> {
			assertThat(item).containsEntry("type", "SERVICE").containsEntry("active", true);
			assertThat(((Number) item.get("price")).doubleValue()).isLessThanOrEqualTo(200.0);
		});
	}

	@Test
	void maximumPageSizeIsAcceptedAndCapsTheResult() {
		Map<String, Object> payload = payload(call(Map.of("pageSize", 100)));

		assertThat(payload).containsEntry("pageSize", 100);
		assertThat(items(payload)).hasSize(24);
		assertThat(asLong(payload, "totalItems")).isEqualTo(24);
	}

	@Test
	void pageBeyondTheResultSetReturnsNoItemsWithCorrectTotals() {
		Map<String, Object> payload = payload(call(Map.of("page", 5, "pageSize", 20)));

		assertThat(items(payload)).isEmpty();
		assertThat(payload).containsEntry("page", 5).containsEntry("pageSize", 20);
		assertThat(asLong(payload, "totalItems")).isEqualTo(24);
		assertThat(asLong(payload, "totalPages")).isEqualTo(2);
	}

	@Test
	void orderingIsDeterministicAcrossIdenticalCalls() {
		List<Long> first = ids(payload(call(Map.of("pageSize", 100))));
		List<Long> second = ids(payload(call(Map.of("pageSize", 100))));

		assertThat(first).isEqualTo(second).isSorted();
		assertThat(first).containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(1, 24).boxed().toList());
	}

	@Test
	void resultDataOriginatesFromTheSeededPostgresCatalog() {
		Map<String, Object> payload = payload(call(Map.of("type", "SERVICE", "pageSize", 100)));

		Map<String, Object> item = items(payload).stream()
			.filter(candidate -> ((Number) candidate.get("id")).longValue() == 16L)
			.findFirst()
			.orElseThrow();

		assertThat(item).containsEntry("sku", "SVC-104")
			.containsEntry("name", "Network Health Assessment")
			.containsEntry("type", "SERVICE")
			.containsEntry("active", true)
			.containsEntry("createdAt", "2026-01-15T09:00:00Z")
			.containsEntry("updatedAt", "2026-01-15T09:00:00Z");
		assertThat((String) item.get("description"))
			.isEqualTo("Review office network configuration and provide a prioritized findings report.");
		assertThat(((Number) item.get("price")).doubleValue()).isEqualTo(199.00);
	}

	@Test
	void pageSizeAboveTheLimitIsRejectedByTheCatalogService() {
		assertThat(errorText(call(Map.of("pageSize", 101))))
			.isEqualTo("Page size must be between 1 and 100");
	}

	@Test
	void pageAboveTheLimitAndNegativePagesAreRejectedByTheCatalogService() {
		assertThat(errorText(call(Map.of("page", -1)))).isEqualTo("Page must be between 0 and 10000");
		assertThat(errorText(call(Map.of("page", 10001)))).isEqualTo("Page must be between 0 and 10000");
	}

	@Test
	void invalidPricesAreRejectedByTheCatalogService() {
		assertThat(errorText(call(Map.of("maxPrice", new BigDecimal("-1")))))
			.isEqualTo("Maximum price must be between 0 and 9999999999.99 with at most two decimal places");
		assertThat(errorText(call(Map.of("maxPrice", new BigDecimal("1.001")))))
			.isEqualTo("Maximum price must be between 0 and 9999999999.99 with at most two decimal places");
	}

	@Test
	void oversizedSearchTextIsRejectedByTheCatalogService() {
		assertThat(errorText(call(Map.of("text", "a".repeat(201)))))
			.isEqualTo("Text must contain at most 200 characters and no NUL character");
	}

	@Test
	void invalidTypeIsRejectedByTheCatalogService() {
		assertThat(errorText(call(Map.of("type", "FOO")))).isEqualTo("Type must be PRODUCT or SERVICE");
	}

	@Test
	void noUnboundedResultSetCanBeRequested() {
		assertThat(errorText(call(Map.of("pageSize", 1_000_000)))).isEqualTo("Page size must be between 1 and 100");
		assertThat(items(payload(call(Map.of("pageSize", 100)))).size()).isLessThanOrEqualTo(100);
	}

	private McpSchema.CallToolResult call(Map<String, Object> arguments) {
		return this.client.callTool(McpSchema.CallToolRequest.builder(TOOL).arguments(arguments).build());
	}

	private static Map<String, Object> payload(McpSchema.CallToolResult result) {
		assertThat(result.isError()).as("expected a successful tool result but got: %s", text(result)).isNotEqualTo(true);
		return new JsonHelper().fromJsonToMap(text(result));
	}

	private static String errorText(McpSchema.CallToolResult result) {
		assertThat(result.isError()).as("expected a tool error result").isEqualTo(true);
		return text(result);
	}

	private static String text(McpSchema.CallToolResult result) {
		assertThat(result.content()).hasSize(1);
		return ((McpSchema.TextContent) result.content().get(0)).text();
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

	@SuppressWarnings("unchecked")
	private static Map<String, Object> properties(Map<String, Object> schema) {
		return (Map<String, Object>) schema.get("properties");
	}

	@SuppressWarnings("unchecked")
	private static String propertyType(Map<String, Object> schema, String name) {
		return (String) ((Map<String, Object>) properties(schema).get(name)).get("type");
	}

}
