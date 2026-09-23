package com.example.mcpcatalog;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.sql.DataSource;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
class McpCatalogApplicationTest {
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
    DataSource dataSource;

    @Test
    void contextLoadsWithRealPostgresAndHealthyHttpEndpoints() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.isValid(5)).isTrue();
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
        try (var client = HttpClient.newHttpClient()) {
            for (String path : new String[]{"/actuator/health", "/actuator/health/readiness"}) {
                var response = client.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(JsonPath.<String>read(response.body(), "$.status")).isEqualTo("UP");
                assertThat(response.body()).doesNotContain("components", "details");
            }
        }
    }
}
