package com.example.mcpcatalog.catalog.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest
class CatalogServicePostgresTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-trixie");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", postgres::getJdbcUrl);
        registry.add("DB_USERNAME", postgres::getUsername);
        registry.add("DB_PASSWORD", postgres::getPassword);
    }

    @Autowired CatalogService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void noFilterSearchDefaultsToFirstTwentyOfTwentyFour() {
        var page = service.search(new CatalogSearchCriteria(null, null, null, null, null, null));
        assertThat(page.page()).isZero();
        assertThat(page.pageSize()).isEqualTo(20);
        assertThat(page.totalItems()).isEqualTo(24);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.items()).extracting(CatalogItemView::id)
                .containsExactlyElementsOf(LongStream.rangeClosed(1, 20).boxed().toList());
        assertThatThrownBy(() -> page.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    static Stream<Arguments> filters() {
        return Stream.of(
                Arguments.of("PRODUCT", null, null, null, LongStream.rangeClosed(1, 12).toArray()),
                Arguments.of("SERVICE", null, null, null, LongStream.rangeClosed(13, 24).toArray()),
                Arguments.of(null, true, null, null, new long[]{1,2,3,4,5,6,7,8,9,13,14,15,16,17,18,19,20,21}),
                Arguments.of(null, false, null, null, new long[]{10,11,12,22,23,24}),
                Arguments.of(null, null, "39.95", null, new long[]{1,6,10,20,22}),
                Arguments.of(null, null, "0", null, new long[]{20}),
                Arguments.of("SERVICE", true, "200", null, new long[]{13,14,15,16,19,20}),
                Arguments.of("SERVICE", true, "200", "NETWORK", new long[]{16,19}),
                Arguments.of("PRODUCT", false, "50", null, new long[]{10}),
                Arguments.of("PRODUCT", null, "0", null, new long[]{}));
    }

    @ParameterizedTest
    @MethodSource("filters")
    void filtersThroughServiceAgainstPostgres(String type, Boolean active, String price, String text, long[] expected) {
        var page = service.search(new CatalogSearchCriteria(type, active,
                price == null ? null : new BigDecimal(price), text, null, 100));
        assertThat(page.items().stream().mapToLong(CatalogItemView::id).toArray()).containsExactly(expected);
        assertThat(page.totalItems()).isEqualTo(expected.length);
        assertThat(page.totalPages()).isEqualTo(expected.length == 0 ? 0 : 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sVc-104", "hEaLtH aSsEsSmEnT", "PRIORITIZED", "  prioritized  "})
    void textMatchesSkuNameAndDescriptionIgnoringCase(String text) {
        assertThat(service.search(new CatalogSearchCriteria(null, null, null, text, null, null)).items())
                .extracting(CatalogItemView::id).containsExactly(16L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "\\", "' OR 1=1 --"})
    void textIsLiteralAndNeverSqlOrWildcardSyntax(String text) {
        var result = service.search(new CatalogSearchCriteria(null, null, null, text, null, null));
        assertThat(result.items()).isEmpty();
        assertThat(result.totalItems()).isZero();
    }

    @Test
    void pagesAreStableDistinctAndOrderedByUniqueId() {
        var ids = new ArrayList<Long>();
        for (int page = 0; page < 5; page++) {
            var criteria = new CatalogSearchCriteria(null, null, null, null, page, 5);
            var result = service.search(criteria);
            assertThat(result).isEqualTo(service.search(criteria));
            assertThat(result.totalItems()).isEqualTo(24);
            assertThat(result.totalPages()).isEqualTo(5);
            assertThat(result.items()).hasSizeLessThanOrEqualTo(5);
            ids.addAll(result.items().stream().map(CatalogItemView::id).toList());
        }
        assertThat(ids).containsExactlyElementsOf(LongStream.rangeClosed(1, 24).boxed().toList());
        var beyond = service.search(new CatalogSearchCriteria(null, null, null, null, 10_000, 100));
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.totalItems()).isEqualTo(24);
    }

    @Test
    @Transactional
    void maximumPageSizeStillLimitsMoreThanOneHundredMatches() {
        jdbc.update("""
                INSERT INTO public.catalog_item (sku, name, type, description, price)
                SELECT 'CAPACITY-' || n, 'Office Supply Kit ' || n, 'PRODUCT',
                       'Office supply kit for pagination capacity verification.', 10.00
                FROM generate_series(1, 105) n
                """);
        var maximum = service.search(new CatalogSearchCriteria(null, null, null, null, 0, 100));
        assertThat(maximum.items()).hasSize(100);
        assertThat(maximum.totalItems()).isEqualTo(129);
        assertThat(maximum.totalPages()).isEqualTo(2);
        assertThat(service.search(null).items()).hasSize(20);
        assertThat(service.search(new CatalogSearchCriteria(null, null, null, null, 1, 100)).items()).hasSize(29);
    }

    @Test
    void detailIsMappedAndMissingRecordIsNotFabricated() {
        var item = service.getItem(1L);
        assertThat(item.sku()).isEqualTo("PRD-101");
        assertThat(item.name()).isEqualTo("Ergonomic Wireless Mouse");
        assertThat(item.type()).isEqualTo("PRODUCT");
        assertThat(item.price()).isEqualByComparingTo("39.95");
        assertThat(item.active()).isTrue();
        assertThat(item.description()).isEqualTo("Wireless mouse with adjustable sensitivity for office workstations.");
        assertThat(item.createdAt().toInstant()).isEqualTo(Instant.parse("2026-01-15T09:00:00Z"));
        assertThat(item.updatedAt()).isEqualTo(item.createdAt());
        assertThat(service.getItem(22L).active()).isFalse();
        assertThatThrownBy(() -> service.getItem(Long.MAX_VALUE)).isInstanceOf(CatalogItemNotFoundException.class);
    }
}
