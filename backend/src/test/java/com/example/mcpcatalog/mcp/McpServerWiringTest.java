package com.example.mcpcatalog.mcp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 4 gate: the MCP server starts, exposes the documented Streamable HTTP
 * endpoint, advertises only the tool capability, registers no catalog tool yet,
 * and enforces request-origin protection.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerWiringTest {

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
	ServerTransportSecurityValidator transportSecurityValidator;

	@Test
	void mcpServerIdentityMatchesTheApplicationVersion() {
		McpSchema.Implementation serverInfo = mcpSyncServer.getServerInfo();

		assertThat(serverInfo.name()).isEqualTo("mcp-catalog-server");
		assertThat(serverInfo.version()).isEqualTo("0.1.0");
	}

	@Test
	void onlyTheToolCapabilityIsAdvertised() {
		McpSchema.ServerCapabilities capabilities = mcpSyncServer.getServerCapabilities();

		assertThat(capabilities).isNotNull();
		assertThat(capabilities.tools()).isNotNull();
		assertThat(capabilities.resources()).as("Slice 4 must not enable MCP resources").isNull();
		assertThat(capabilities.prompts()).as("Slice 4 must not enable MCP prompts").isNull();
		assertThat(capabilities.completions()).as("Slice 4 must not enable MCP completions").isNull();
	}

	@Test
	void exactlyTheAcceptedCatalogToolsAreRegistered() {
		assertThat(mcpSyncServer.listTools()).extracting(McpSchema.Tool::name)
			.as("search_catalog (Slice 5) and get_catalog_item (Slice 6) are the only production tools")
			.containsExactlyInAnyOrder("search_catalog", "get_catalog_item");
	}

	@Test
	void streamableTransportUsesOriginValidatingProviderInsteadOfSdkNoop() {
		assertThat(streamableTransportProvider).isNotNull();
		assertThat(transportSecurityValidator).isInstanceOf(DefaultServerTransportSecurityValidator.class);
		assertThat(transportSecurityValidator).isNotSameAs(ServerTransportSecurityValidator.NOOP);
	}

	@Test
	void documentedMcpEndpointIsRegisteredAtSlashMcp() throws Exception {
		try (HttpClient client = HttpClient.newHttpClient()) {
			HttpResponse<String> response = client.send(
					HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp")).GET().build(),
					HttpResponse.BodyHandlers.ofString());

			assertThat(response.statusCode()).as("a registered endpoint answers 400, not 404").isEqualTo(400);
			assertThat(response.body()).contains("Accept");
		}
	}

	@Test
	void crossOriginMcpRequestIsRejectedBeforeJsonRpcHandling() throws Exception {
		HttpResponse<String> response = postMcp(null, "http://evil.example");

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(response.body()).isEqualTo("Invalid Origin header");
	}

	@Test
	void loopbackOriginMcpRequestPassesOriginValidation() throws Exception {
		HttpResponse<String> response = postMcp(null, "http://127.0.0.1:" + port);

		assertThat(response.statusCode()).as("origin accepted, so the body is parsed").isEqualTo(400);
		assertThat(response.body()).contains("Invalid message format");
	}

	@Test
	void requestWithoutOriginHeaderIsAllowedForNonBrowserClients() throws Exception {
		HttpResponse<String> response = postMcp(null, null);

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.body()).contains("Invalid message format");
	}

	@Test
	void nonLoopbackHostHeaderIsRejectedWith421() throws Exception {
		String rawResponse = rawPostWithHost("evil.example:8080");

		assertThat(rawResponse).startsWith("HTTP/1.1 421");
		assertThat(rawResponse).contains("Invalid Host header");
	}

	@Test
	void loopbackHostHeaderIsAccepted() throws Exception {
		String rawResponse = rawPostWithHost("127.0.0.1:" + port);

		assertThat(rawResponse).startsWith("HTTP/1.1 400");
		assertThat(rawResponse).contains("Invalid message format");
	}

	private HttpResponse<String> postMcp(String accept, String origin) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
			.header("Content-Type", "application/json")
			.header("Accept", accept != null ? accept : "application/json, text/event-stream")
			.POST(HttpRequest.BodyPublishers.ofString("not-json"));
		if (origin != null) {
			request.header("Origin", origin);
		}
		try (HttpClient client = HttpClient.newHttpClient()) {
			return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}
	}

	/**
	 * The JDK HTTP client refuses to set a synthetic {@code Host} header, so the
	 * DNS-rebinding case is exercised over a raw socket.
	 */
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
