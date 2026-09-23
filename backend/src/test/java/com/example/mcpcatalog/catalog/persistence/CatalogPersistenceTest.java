package com.example.mcpcatalog.catalog.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class CatalogPersistenceTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-trixie");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", postgres::getJdbcUrl);
        registry.add("DB_USERNAME", postgres::getUsername);
        registry.add("DB_PASSWORD", postgres::getPassword);
    }

    @Autowired CatalogItemRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test
    void migrationsAreAppliedValidatedAndNotReapplied() {
        flyway.validate();
        assertThat(Arrays.stream(flyway.info().applied())
                .map(migration -> migration.getVersion().getVersion())).containsExactly("1", "2");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.flyway_schema_history WHERE success", Long.class))
                .isEqualTo(2L);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(repository.count()).isEqualTo(24);
    }

    @Test
    void allSeedRowsMapAndCoverBothTypesAndActiveStates() {
        var items = jdbc.queryForList("SELECT id FROM public.catalog_item ORDER BY id", Long.class)
                .stream().map(id -> repository.findById(id).orElseThrow()).toList();
        assertThat(items).hasSize(24);
        assertThat(items).filteredOn(item -> item.type() == CatalogItemType.PRODUCT).hasSize(12);
        assertThat(items).filteredOn(item -> item.type() == CatalogItemType.SERVICE).hasSize(12);
        for (var type : CatalogItemType.values()) {
            assertThat(items).filteredOn(item -> item.type() == type && item.active()).hasSize(9);
            assertThat(items).filteredOn(item -> item.type() == type && !item.active()).hasSize(3);
        }
        assertThat(items).allSatisfy(item -> {
            assertThat(item.sku()).isNotBlank();
            assertThat(item.name()).isNotBlank();
            assertThat(item.description()).isNotBlank();
            assertThat(item.price()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(item.createdAt()).isNotNull();
            assertThat(item.updatedAt().toInstant()).isAfterOrEqualTo(item.createdAt().toInstant());
        });
        assertThat(items).anySatisfy(item -> assertThat(item.price()).isLessThan(new BigDecimal("50")));
        assertThat(items).anySatisfy(item -> assertThat(item.price()).isGreaterThan(new BigDecimal("1000")));
    }

    @Test
    void retrievesKnownProductAndServiceDeterministicallyAndMissingRowsAreEmpty() {
        var product = repository.findBySku("PRD-101").orElseThrow();
        assertThat(product.id()).isEqualTo(1L);
        assertThat(product.name()).isEqualTo("Ergonomic Wireless Mouse");
        assertThat(product.description()).isEqualTo("Wireless mouse with adjustable sensitivity for office workstations.");
        assertThat(product.price()).isEqualByComparingTo("39.95");
        assertThat(product.type()).isEqualTo(CatalogItemType.PRODUCT);
        assertThat(product.active()).isTrue();
        assertThat(product.createdAt().toInstant()).isEqualTo(Instant.parse("2026-01-15T09:00:00Z"));
        assertThat(product.updatedAt()).isEqualTo(product.createdAt());
        assertThat(repository.findById(1L)).contains(product);
        var service = repository.findById(16L).orElseThrow();
        assertThat(service.sku()).isEqualTo("SVC-104");
        assertThat(service.type()).isEqualTo(CatalogItemType.SERVICE);
        assertThat(service.price()).isEqualByComparingTo("199.00");
        assertThat(repository.findById(Long.MAX_VALUE)).isEmpty();
        assertThat(repository.findBySku("UNKNOWN-SKU")).isEmpty();
        assertThat(repository.findBySku("prd-101")).isEmpty();
    }

    @Test
    @Transactional
    void identityAfterSeedDoesNotCollideAndDatabaseSuppliesTimestamps() {
        Long id = jdbc.queryForObject("""
                INSERT INTO public.catalog_item (sku, name, type, description, price)
                VALUES ('PRD-IDENTITY-CHECK', 'Equipment Storage Tray', 'PRODUCT',
                        'Stackable storage tray for office equipment.', 12.50) RETURNING id
                """, Long.class);
        assertThat(id).isGreaterThan(24L);
        var item = repository.findById(id).orElseThrow();
        assertThat(item.active()).isTrue();
        assertThat(item.createdAt()).isNotNull();
        assertThat(item.updatedAt()).isEqualTo(item.createdAt());
    }

    @Test
    void databaseRejectsInvalidTypesPricesAndDuplicateSkus() {
        assertThatThrownBy(() -> jdbc.update("UPDATE public.catalog_item SET type = 'BUNDLE' WHERE id = 1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE public.catalog_item SET price = -1 WHERE id = 1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE public.catalog_item SET sku = 'PRD-101' WHERE id = 2"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
