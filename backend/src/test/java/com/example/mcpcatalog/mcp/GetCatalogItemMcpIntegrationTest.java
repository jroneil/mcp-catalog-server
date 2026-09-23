package com.example.mcpcatalog.mcp;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Slice 6 gate: a real MCP client drives the production {@code get_catalog_item}
 * tool over Streamable HTTP against the seeded PostgreSQL catalog, alongside the
 * unchanged {@code search_catalog} tool.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GetCatalogItemMcpIntegrationTest {

	private static final String DETAIL_TOOL = "get_catalog_item";

	private static final String SEARCH_TOOL = "search_catalog";

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
			.clientInfo(new McpSchema.Implementation("slice6-test-client", "0.1.0"))
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
	void exactlyTheTwoAcceptedProductionToolsAreRegistered() {
		assertThat(this.client.listTools().tools()).extracting(McpSchema.Tool::name)
			.containsExactlyInAnyOrder(SEARCH_TOOL, DETAIL_TOOL);
	}

	@Test
	void getCatalogItemIsDiscoverableWithTheDocumentedSchema() {
		McpSchema.Tool tool = findTool(DETAIL_TOOL);

		assertThat(tool.description()).contains("catalog item", "identifier");
		Map<String, Object> schema = tool.inputSchema();
		assertThat(schema).containsEntry("type", "object").containsEntry("additionalProperties", false);
		assertThat(schema).containsEntry("required", List.of("id"));
		assertThat(properties(schema)).containsOnlyKeys("id");
		assertThat(propertyType(schema, "id")).isEqualTo("integer");
		assertThat((String) property(schema, "id").get("description")).isNotBlank();
		assertThat(property(schema, "id")).doesNotContainKeys("enum", "minimum", "maximum");
	}

	@Test
	void knownSeededIdentifierReturnsTheExactStoredRow() {
		Map<String, Object> item = payload(call(DETAIL_TOOL, Map.of("id", 16)));

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

	@Test
	void inactiveSeededIdentifierIsRetrievableByIdentifier() {
		Map<String, Object> item = payload(call(DETAIL_TOOL, Map.of("id", 22)));

		assertThat(item).containsEntry("id", 22)
			.containsEntry("sku", "SVC-110")
			.containsEntry("name", "Legacy Email Account Setup")
			.containsEntry("type", "SERVICE")
			.containsEntry("active", false);
		assertThat(((Number) item.get("price")).doubleValue()).isEqualTo(35.00);
	}

	@Test
	void detailResultIsIdenticalToTheSearchCatalogItemForTheSameRow() {
		Map<String, Object> detail = payload(call(DETAIL_TOOL, Map.of("id", 16)));
		Map<String, Object> search = payload(call(SEARCH_TOOL, Map.of("text", "SVC-104")));

		assertThat(items(search)).hasSize(1);
		assertThat(detail).isEqualTo(items(search).get(0));
		assertThat(detail).containsEntry("createdAt", "2026-01-15T09:00:00Z")
			.containsEntry("updatedAt", "2026-01-15T09:00:00Z");
	}

	@Test
	void unknownIdentifierReturnsTheDefinedNotFoundError() {
		McpSchema.CallToolResult result = call(DETAIL_TOOL, Map.of("id", 99999));

		assertThat(result.isError()).isEqualTo(true);
		assertThat(text(result)).isEqualTo("Catalog item not found: 99999");
		assertNoInternalDetails(text(result));
	}

	@Test
	void notFoundResultDoesNotFabricateItemData() {
		McpSchema.CallToolResult result = call(DETAIL_TOOL, Map.of("id", 99999));

		assertThat(result.isError()).isEqualTo(true);
		assertThat(text(result)).doesNotStartWith("{").doesNotContain("\"sku\"", "\"items\"");
	}

	@Test
	void nonPositiveIdentifiersAreRejectedCleanlyByTheCatalogService() {
		for (Object id : List.of(0, -5)) {
			McpSchema.CallToolResult result = call(DETAIL_TOOL, Map.of("id", id));

			assertThat(result.isError()).as("id %s", id).isEqualTo(true);
			assertThat(text(result)).isEqualTo("Catalog item ID must be positive");
			assertNoInternalDetails(text(result));
		}
	}

	@Test
	void missingIdentifierIsRejectedBeforeTheHandlerRuns() {
		McpSchema.CallToolResult result = call(DETAIL_TOOL, Map.of());

		assertThat(result.isError()).isEqualTo(true);
		assertNoInternalDetails(text(result));
	}

	@Test
	void nonIntegerIdentifierIsRejectedCleanly() {
		McpSchema.CallToolResult result = call(DETAIL_TOOL, Map.of("id", "not-a-number"));

		assertThat(result.isError()).isEqualTo(true);
		assertNoInternalDetails(text(result));
	}

	@Test
	void searchCatalogStillWorksAlongsideTheDetailTool() {
		Map<String, Object> search = payload(call(SEARCH_TOOL,
				Map.of("type", "SERVICE", "active", true, "maxPrice", 200, "page", 0, "pageSize", 20)));

		assertThat(asLong(search, "totalItems")).isEqualTo(6);
		assertThat(ids(search)).containsExactly(13L, 14L, 15L, 16L, 19L, 20L);
	}

	@Test
	void detailLookupIsBackedByPostgresForBothKnownRows() {
		Map<String, Object> first = payload(call(DETAIL_TOOL, Map.of("id", 1)));
		Map<String, Object> sixth = payload(call(DETAIL_TOOL, Map.of("id", 6)));

		assertThat(first).containsEntry("sku", "PRD-101")
			.containsEntry("name", "Ergonomic Wireless Mouse")
			.containsEntry("type", "PRODUCT")
			.containsEntry("active", true);
		assertThat(((Number) first.get("price")).doubleValue()).isEqualTo(39.95);
		assertThat(sixth).containsEntry("sku", "PRD-106").containsEntry("active", true);
		assertThat(((Number) sixth.get("price")).doubleValue()).isEqualTo(24.00);
	}

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
		assertThat(result.isError()).as("expected a successful tool result but got: %s", text(result)).isNotEqualTo(true);
		return new JsonHelper().fromJsonToMap(text(result));
	}

	private static String text(McpSchema.CallToolResult result) {
		assertThat(result.content()).hasSize(1);
		return ((McpSchema.TextContent) result.content().get(0)).text();
	}

	private static void assertNoInternalDetails(String errorText) {
		assertThat(errorText).doesNotContain("Exception", "java.", "at com.", "org.springframework", "jdbc:",
				"password", "SELECT ");
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
	private static Map<String, Object> property(Map<String, Object> schema, String name) {
		return (Map<String, Object>) properties(schema).get(name);
	}

	private static String propertyType(Map<String, Object> schema, String name) {
		return (String) property(schema, name).get("type");
	}

}
