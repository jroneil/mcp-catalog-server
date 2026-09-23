package com.example.mcpcatalog.rest;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.util.JsonHelper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogRestIntegrationTest {
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

    static Stream<Map<String, Object>> searches() {
        return Stream.of(Map.of(), Map.of("type", "PRODUCT"), Map.of("type", "SERVICE"),
                Map.of("active", true), Map.of("active", false), Map.of("maxPrice", new BigDecimal("199.00")),
                Map.of("text", "NETWORK"), Map.of("text", "network"), Map.of("text", "%_!"),
                Map.of("text", "  "), Map.of("page", 1), Map.of("pageSize", 100),
                Map.of("page", 10000), Map.of("pageSize", 1, "page", 2),
                Map.of("type", "SERVICE", "active", true, "maxPrice", 200, "text", "network", "pageSize", 5));
    }

    @ParameterizedTest
    @MethodSource("searches")
    void searchMatchesAcceptedMcpResultAgainstPostgres(Map<String, Object> criteria) throws Exception {
        var response = get("/api/v1/catalog" + query(criteria));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("application/json");
        var rest = json(response.body());
        assertThat(rest).containsOnlyKeys("items", "page", "pageSize", "totalItems", "totalPages");
        try (var client = connect()) {
            assertThat(rest).isEqualTo(payload(client.callTool(
                    McpSchema.CallToolRequest.builder("search_catalog").arguments(criteria).build())));
        }
    }

    @Test
    void defaultsAndStablePageBoundariesArePreserved() throws Exception {
        var first = json(get("/api/v1/catalog").body());
        var second = json(get("/api/v1/catalog?page=1").body());
        assertThat(first).containsEntry("page", 0).containsEntry("pageSize", 20)
                .containsEntry("totalItems", 24).containsEntry("totalPages", 2);
        assertThat(ids(first)).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 20).boxed().toList());
        assertThat(ids(second)).containsExactly(21, 22, 23, 24);
        assertThat(json(get("/api/v1/catalog").body())).isEqualTo(first);
    }

    @ParameterizedTest
    @ValueSource(longs = {16, 22})
    void detailIncludingInactiveItemMatchesMcpAndSearch(long id) throws Exception {
        var response = get("/api/v1/catalog/" + id);
        assertThat(response.statusCode()).isEqualTo(200);
        var rest = json(response.body());
        assertThat(rest).containsOnlyKeys("id", "sku", "name", "type", "description", "price", "active", "createdAt", "updatedAt");
        assertThat(rest).containsEntry("active", id == 16);
        try (var client = connect()) {
            assertThat(rest).isEqualTo(payload(client.callTool(
                    McpSchema.CallToolRequest.builder("get_catalog_item").arguments(Map.of("id", id)).build())));
        }
        var search = json(get("/api/v1/catalog?text=" + rest.get("sku")).body());
        assertThat((List<?>) search.get("items")).hasSize(1);
        assertThat(((List<?>) search.get("items")).getFirst()).isEqualTo(rest);
    }

    static Stream<Map<String, Object>> invalidCriteria() {
        return Stream.of(Map.of("type", "product"), Map.of("type", "UNKNOWN"),
                Map.of("page", -1), Map.of("page", 10001), Map.of("pageSize", 0), Map.of("pageSize", 101),
                Map.of("maxPrice", -1), Map.of("maxPrice", new BigDecimal("10000000000")),
                Map.of("text", "a".repeat(201)));
    }

    @ParameterizedTest
    @MethodSource("invalidCriteria")
    void serviceValidationMapsTo400WithTheSameSafeMessageAsMcp(Map<String, Object> criteria) throws Exception {
        var response = get("/api/v1/catalog" + query(criteria));
        var error = assertError(response, 400, "Bad Request", "/api/v1/catalog");
        try (var client = connect()) {
            var result = client.callTool(McpSchema.CallToolRequest.builder("search_catalog").arguments(criteria).build());
            assertThat(result.isError()).isTrue();
            assertThat(error.get("message")).isEqualTo(((McpSchema.TextContent) result.content().getFirst()).text());
        }
    }

    @Test
    void decimalScaleIsPreservedForServiceValidation() throws Exception {
        var error = assertError(get("/api/v1/catalog?maxPrice=1.000"), 400, "Bad Request", "/api/v1/catalog");
        assertThat(error).containsEntry("message",
                "Maximum price must be between 0 and 9999999999.99 with at most two decimal places");
    }

    @ParameterizedTest
    @ValueSource(strings = {"active=garbage", "page=1.5", "pageSize=huge", "maxPrice=NaN"})
    void malformedQueryValuesReturnSafe400(String query) throws Exception {
        var error = assertError(get("/api/v1/catalog?" + query), 400, "Bad Request", "/api/v1/catalog");
        assertThat(error).containsEntry("message", "Malformed request parameter");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "nonnumeric", "9223372036854775808"})
    void invalidIdentifiersReturn400(String id) throws Exception {
        assertError(get("/api/v1/catalog/" + id), 400, "Bad Request", "/api/v1/catalog/" + id);
    }

    @Test
    void missingItemReturns404() throws Exception {
        var error = assertError(get("/api/v1/catalog/99999"), 404, "Not Found", "/api/v1/catalog/99999");
        assertThat(error).containsEntry("message", "Catalog item not found: 99999");
    }

    private Map<String, Object> assertError(HttpResponse<String> response, int status, String reason, String path) {
        assertThat(response.statusCode()).isEqualTo(status);
        var error = json(response.body());
        assertThat(error).containsOnlyKeys("status", "error", "message", "path")
                .containsEntry("status", status).containsEntry("error", reason).containsEntry("path", path);
        assertThat(response.body()).doesNotContain("Exception", "java.", "jdbc:", "SELECT ", "password", "stackTrace");
        return error;
    }

    private HttpResponse<String> get(String path) throws Exception {
        try (var http = HttpClient.newHttpClient()) {
            return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private McpSyncClient connect() {
        var client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                .endpoint("/mcp").build()).requestTimeout(Duration.ofSeconds(20)).build();
        client.initialize();
        return client;
    }

    private static String query(Map<String, Object> values) {
        return values.isEmpty() ? "" : "?" + values.entrySet().stream().map(entry -> entry.getKey() + "="
                + URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
    }

    private static Map<String, Object> json(String value) {
        return new JsonHelper().fromJsonToMap(value);
    }

    private static Map<String, Object> payload(McpSchema.CallToolResult result) {
        assertThat(result.isError()).isNotEqualTo(true);
        return json(((McpSchema.TextContent) result.content().getFirst()).text());
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> ids(Map<String, Object> page) {
        return ((List<Map<String, Object>>) page.get("items")).stream().map(item -> (Integer) item.get("id")).toList();
    }
}
