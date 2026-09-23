package com.example.mcpcatalog.catalog.persistence;

import java.sql.DriverManager;

import com.example.mcpcatalog.McpCatalogApplication;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Testcontainers
class FlywayStartupFailureTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-trixie");

    @Test
    void migrationFailureAbortsApplicationStartupAndRollsBackFailedMigration() throws Exception {
        Throwable failure = catchThrowable(() -> {
            try (var ignored = new SpringApplicationBuilder(McpCatalogApplication.class)
                    .web(WebApplicationType.NONE).run(
                            "--DB_URL=" + postgres.getJdbcUrl(),
                            "--DB_USERNAME=" + postgres.getUsername(),
                            "--DB_PASSWORD=" + postgres.getPassword(),
                            "--spring.flyway.locations=classpath:db/migration,classpath:db/invalid")) {
                // Reaching a running context means the test must fail below.
            }
        });
        assertThat(failure).isNotNull();
        boolean causedByFlyway = false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            causedByFlyway |= cause instanceof FlywayException;
        }
        assertThat(causedByFlyway).isTrue();
        assertThat(failure).hasStackTraceContaining("V3__deliberate_failure.sql");
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT to_regclass('public.migration_failure_probe') IS NULL")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getBoolean(1)).isTrue();
        }
    }
}
