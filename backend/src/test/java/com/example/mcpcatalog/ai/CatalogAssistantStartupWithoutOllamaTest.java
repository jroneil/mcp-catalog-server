package com.example.mcpcatalog.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 14 (D7): the application must start and keep serving the accepted REST, MCP and
 * catalog behavior while the local Ollama provider is unreachable, and startup must not
 * contact it. Only the assistant endpoint may be affected, and no credentials are needed.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.ai.ollama.base-url=http://127.0.0.1:1", "catalog.ai.timeout=2s" })
class CatalogAssistantStartupWithoutOllamaTest {

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

	@Test
	void startsAndServesTheAcceptedBehaviorWithoutOllama() throws Exception {
		assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
		assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);

		HttpResponse<String> search = get("/api/v1/catalog?type=SERVICE&active=true&maxPrice=200");
		assertThat(search.statusCode()).isEqualTo(200);
		assertThat(search.body()).contains("SVC-104").contains("\"totalItems\":6");

		assertThat(get("/api/v1/catalog/16").statusCode()).isEqualTo(200);
		assertThat(get("/api/v1/catalog/99999").statusCode()).isEqualTo(404);

		try (McpSyncClient client = McpClient
			.sync(HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port).endpoint("/mcp").build())
			.requestTimeout(Duration.ofSeconds(20))
			.build()) {
			client.initialize();
			assertThat(client.listTools().tools().stream().map(McpSchema.Tool::name).sorted().toList())
				.containsExactly("get_catalog_item", "search_catalog");
		}
	}

	@Test
	void reportsOnlyTheAssistantAsUnavailableWhenTheProviderIsUnreachable() throws Exception {
		HttpResponse<String> response = post("/api/v1/catalog/assistant",
				"{\"prompt\":\"Show me active service items under $200.\"}");

		assertThat(response.statusCode()).isEqualTo(503);
		assertThat(response.body()).contains("The catalog assistant is currently unavailable.")
			.doesNotContain("11434")
			.doesNotContain("Connection refused")
			.doesNotContain("Exception");
	}

	private HttpResponse<String> get(String path) throws Exception {
		return send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.port + path)).GET().build());
	}

	private HttpResponse<String> post(String path, String body) throws Exception {
		return send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.port + path))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build());
	}

	private static HttpResponse<String> send(HttpRequest request) throws Exception {
		try (HttpClient client = HttpClient.newHttpClient()) {
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}
	}

}
