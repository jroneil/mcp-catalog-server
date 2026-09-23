package com.example.mcpcatalog.mcp;

import java.time.Duration;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 4 gate: proves the tool registration/discovery mechanism without shipping a
 * catalog tool. The only tool in this context is a test-scoped probe; the tool
 * behaviour required by the PRD arrives in Slices 5 and 6.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpToolDiscoveryTest {

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

	@Test
	void registryInspectsTheRegisteredToolSpecification() {
		assertThat(mcpSyncServer.listTools()).extracting(McpSchema.Tool::name)
			.containsExactlyInAnyOrder("slice4_probe", "search_catalog", "get_catalog_item");
	}

	@Test
	void mcpClientInitializesDiscoversAndReadsTheToolSchema() {
		McpSyncClient client = McpClient
			.sync(HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + this.port)
				.endpoint("/mcp")
				.build())
			.clientInfo(new McpSchema.Implementation("slice4-test-client", "0.1.0"))
			.requestTimeout(Duration.ofSeconds(20))
			.build();

		try (client) {
			McpSchema.InitializeResult initialization = client.initialize();
			assertThat(initialization.serverInfo().name()).isEqualTo("mcp-catalog-server");
			assertThat(initialization.serverInfo().version()).isEqualTo("0.1.0");

			McpSchema.ListToolsResult discovered = client.listTools();
			assertThat(discovered.tools()).extracting(McpSchema.Tool::name)
				.containsExactlyInAnyOrder("slice4_probe", "search_catalog", "get_catalog_item");

			McpSchema.Tool tool = discovered.tools()
				.stream()
				.filter(candidate -> candidate.name().equals("slice4_probe"))
				.findFirst()
				.orElseThrow();
			assertThat(tool.description()).isNotBlank();
			Map<String, Object> inputSchema = tool.inputSchema();
			assertThat(inputSchema).containsEntry("type", "object");
			assertThat(toolProperties(inputSchema)).containsKey("value");
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> toolProperties(Map<String, Object> inputSchema) {
		return (Map<String, Object>) inputSchema.get("properties");
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ProbeToolConfiguration {

		@Bean
		ToolCallback slice4ProbeTool() {
			return ToolCallbacks.from(new ProbeTool())[0];
		}

	}

	public static class ProbeTool {

		@Tool(name = "slice4_probe",
				description = "Test-only probe used to verify MCP tool discovery mechanics. Not part of the shipped catalog server.")
		public String probe(@ToolParam(description = "Value echoed back by the probe.") String value) {
			return "probe:" + value;
		}

	}

}
