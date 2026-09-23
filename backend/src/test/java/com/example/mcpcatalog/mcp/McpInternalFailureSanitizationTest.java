package com.example.mcpcatalog.mcp;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.mcpcatalog.catalog.application.CatalogSearchCriteria;
import com.example.mcpcatalog.catalog.application.CatalogService;
import com.example.mcpcatalog.mcp.tools.SanitizedToolFailureException;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Slice 7 failure-path hardening: an unexpected internal failure at the
 * {@link CatalogService} boundary is injected through a Mockito spy and driven through
 * the real MCP server with a real client over Streamable HTTP. The client must see only
 * the fixed sanitized message, while the application's intended validation and
 * not-found messages keep passing through unchanged.
 *
 * <p>No production persistence behavior is changed: the spy raises synthetic failures
 * only for sentinel inputs and delegates everything else to the real service.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpInternalFailureSanitizationTest {

	private static final String SEARCH_TRIGGER = "SLICE7_TRIGGER_INTERNAL_FAILURE";

	private static final long DETAIL_TRIGGER_ID = 987654L;

	private static final String INTERNAL_DETAILS = "simulated internal failure: "
			+ "SELECT id, sku, name FROM public.catalog_item WHERE name ILIKE ?; "
			+ "jdbc:postgresql://postgres:5432/catalog?user=catalog&password=sup3r-secret; "
			+ "at com.example.mcpcatalog.catalog.persistence.CatalogItemSearchImpl.search(CatalogItemSearchImpl.java:42); "
			+ "Caused by: org.postgresql.util.PSQLException: FATAL: password authentication failed for user \"catalog\"";

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-trixie");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("DB_URL", postgres::getJdbcUrl);
		registry.add("DB_USERNAME", postgres::getUsername);
		registry.add("DB_PASSWORD", postgres::getPassword);
	}

	@MockitoSpyBean
	CatalogService catalogService;

	@LocalServerPort
	int port;

	private McpSyncClient client;

	@BeforeEach
	void connectInitializeAndInjectFailure() {
		doAnswer(invocation -> {
			if (SEARCH_TRIGGER.equals(invocation.getArgument(0, CatalogSearchCriteria.class).text())) {
				throw new IllegalStateException(INTERNAL_DETAILS);
			}
			return invocation.callRealMethod();
		}).when(this.catalogService).search(any());

		doAnswer(invocation -> {
			if (Long.valueOf(DETAIL_TRIGGER_ID).equals(invocation.getArgument(0, Long.class))) {
				throw new IllegalStateException(INTERNAL_DETAILS);
			}
			return invocation.callRealMethod();
		}).when(this.catalogService).getItem(any());

		this.client = McpClient
			.sync(HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + this.port)
				.endpoint("/mcp")
				.build())
			.clientInfo(new McpSchema.Implementation("slice7-failure-client", "0.1.0"))
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
	void unexpectedSearchFailureReturnsOnlyTheFixedSanitizedMessage() {
		McpSchema.CallToolResult result = call("search_catalog", Map.of("text", SEARCH_TRIGGER));

		assertThat(result.isError()).isEqualTo(true);
		assertThat(errorText(result)).isEqualTo(SanitizedToolFailureException.MESSAGE);
		assertNoInternalDetails(errorText(result));
	}

	@Test
	void unexpectedDetailFailureReturnsOnlyTheFixedSanitizedMessage() {
		McpSchema.CallToolResult result = call("get_catalog_item", Map.of("id", DETAIL_TRIGGER_ID));

		assertThat(result.isError()).isEqualTo(true);
		assertThat(errorText(result)).isEqualTo(SanitizedToolFailureException.MESSAGE);
		assertNoInternalDetails(errorText(result));
	}

	@Test
	void unexpectedFailureDoesNotEchoInjectedDetailsAnywhereInTheResult() {
		McpSchema.CallToolResult result = call("search_catalog", Map.of("text", SEARCH_TRIGGER));

		assertThat(result.toString()).doesNotContain(INTERNAL_DETAILS)
			.doesNotContain("SELECT", "sup3r-secret", "jdbc:", "PSQLException", "CatalogItemSearchImpl");
	}

	@Test
	void invalidSearchCriteriaStillReturnsTheIntendedServiceMessages() {
		assertThat(errorText(call("search_catalog", Map.of("pageSize", 101))))
			.isEqualTo("Page size must be between 1 and 100");
		assertThat(errorText(call("search_catalog", Map.of("type", "FOO"))))
			.isEqualTo("Type must be PRODUCT or SERVICE");
		assertThat(errorText(call("search_catalog", Map.of("text", "a".repeat(201)))))
			.isEqualTo("Text must contain at most 200 characters and no NUL character");
	}

	@Test
	void invalidDetailIdentifierStillReturnsTheIntendedServiceMessage() {
		assertThat(errorText(call("get_catalog_item", Map.of("id", 0))))
			.isEqualTo("Catalog item ID must be positive");
		assertThat(errorText(call("get_catalog_item", Map.of("id", -5))))
			.isEqualTo("Catalog item ID must be positive");
	}

	@Test
	void notFoundStillReturnsTheIntendedServiceMessage() {
		McpSchema.CallToolResult result = call("get_catalog_item", Map.of("id", 99999));

		assertThat(result.isError()).isEqualTo(true);
		assertThat(errorText(result)).isEqualTo("Catalog item not found: 99999");
		assertNoInternalDetails(errorText(result));
	}

	@Test
	void sanitizationDoesNotAffectSuccessfulCalls() {
		Map<String, Object> payload = new JsonHelper()
			.fromJsonToMap(successText(call("search_catalog", Map.of("text", "SVC-104"))));

		assertThat(payload).containsEntry("totalItems", 1).containsEntry("totalPages", 1);
		assertThat(successText(call("get_catalog_item", Map.of("id", 16)))).contains("\"sku\":\"SVC-104\"");
	}

	private McpSchema.CallToolResult call(String tool, Map<String, Object> arguments) {
		return this.client.callTool(McpSchema.CallToolRequest.builder(tool)
			.arguments(new LinkedHashMap<>(arguments))
			.build());
	}

	private static String errorText(McpSchema.CallToolResult result) {
		assertThat(result.isError()).as("expected an error result").isEqualTo(true);
		return singleTextBlock(result);
	}

	private static String successText(McpSchema.CallToolResult result) {
		assertThat(result.isError()).as("expected a successful result").isNotEqualTo(true);
		return singleTextBlock(result);
	}

	private static String singleTextBlock(McpSchema.CallToolResult result) {
		assertThat(result.content()).hasSize(1);
		return ((McpSchema.TextContent) result.content().get(0)).text();
	}

	private static void assertNoInternalDetails(String errorText) {
		assertThat(errorText).doesNotContain("SELECT", "public.catalog_item", "jdbc:", "postgresql", "password",
				"sup3r-secret", "Caused by", "Exception", "at com.", ".java:", "/app", "PSQLException");
	}

}
