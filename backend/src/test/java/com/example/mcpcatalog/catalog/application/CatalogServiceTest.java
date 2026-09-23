package com.example.mcpcatalog.catalog.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.example.mcpcatalog.catalog.persistence.CatalogItemRepository;
import com.example.mcpcatalog.catalog.persistence.CatalogItemSearch.SearchRows;
import com.example.mcpcatalog.catalog.persistence.CatalogItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogServiceTest {
    private final CatalogItemRepository repository = mock(CatalogItemRepository.class);
    private final CatalogService service = new CatalogService(repository);

    @Test
    void defaultsAndNormalizationBelongToService() {
        when(repository.search(null, null, null, null, 20, 0)).thenReturn(new SearchRows(List.of(), 0));
        var result = service.search(null);
        assertThat(result.page()).isZero();
        assertThat(result.pageSize()).isEqualTo(20);
        assertThat(result.totalItems()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(service.search(new CatalogSearchCriteria(null, null, null, "   ", null, null))).isEqualTo(result);
        verify(repository, times(2)).search(null, null, null, null, 20, 0);
    }

    @Test
    void passesValidatedFiltersAndCalculatedOffsetToPersistence() {
        var price = new BigDecimal("200.00");
        when(repository.search(CatalogItemType.SERVICE, false, price, "network", 5, 10))
                .thenReturn(new SearchRows(List.of(), 11));
        var page = service.search(new CatalogSearchCriteria("SERVICE", false, price, " network ", 2, 5));
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.totalItems()).isEqualTo(11);
        assertThat(page.page()).isEqualTo(2);
        verify(repository).search(CatalogItemType.SERVICE, false, price, "network", 5, 10);
    }

    @Test
    void acceptsUpperBoundsWithoutUnboundedQuery() {
        String text = "a".repeat(200);
        when(repository.search(null, null, CatalogService.MAX_PRICE, text, 100, 1_000_000L))
                .thenReturn(new SearchRows(List.of(), 0));
        service.search(new CatalogSearchCriteria(null, null, CatalogService.MAX_PRICE, text, 10_000, 100));
        verify(repository).search(null, null, CatalogService.MAX_PRICE, text, 100, 1_000_000L);
    }

    static Stream<Arguments> invalidCriteria() {
        return Stream.of(
                Arguments.of(new CatalogSearchCriteria("OTHER", null, null, null, null, null), "type"),
                Arguments.of(new CatalogSearchCriteria("product", null, null, null, null, null), "type"),
                Arguments.of(new CatalogSearchCriteria("", null, null, null, null, null), "type"),
                Arguments.of(new CatalogSearchCriteria(" PRODUCT ", null, null, null, null, null), "type"),
                Arguments.of(new CatalogSearchCriteria(null, null, new BigDecimal("-0.01"), null, null, null), "maxPrice"),
                Arguments.of(new CatalogSearchCriteria(null, null, new BigDecimal("10000000000"), null, null, null), "maxPrice"),
                Arguments.of(new CatalogSearchCriteria(null, null, new BigDecimal("1.001"), null, null, null), "maxPrice"),
                Arguments.of(new CatalogSearchCriteria(null, null, new BigDecimal("1.000"), null, null, null), "maxPrice"),
                Arguments.of(new CatalogSearchCriteria(null, null, null, "a".repeat(201), null, null), "text"),
                Arguments.of(new CatalogSearchCriteria(null, null, null, " ".repeat(201), null, null), "text"),
                Arguments.of(new CatalogSearchCriteria(null, null, null, "a\0b", null, null), "text"),
                Arguments.of(new CatalogSearchCriteria(null, null, null, null, -1, null), "page"),
                Arguments.of(new CatalogSearchCriteria(null, null, null, null, 10_001, null), "page"),
                Arguments.of(new CatalogSearchCriteria(null, null, null, null, Integer.MAX_VALUE, null), "page"),
                Arguments.of(new CatalogSearchCriteria(null, null, null, null, null, 0), "pageSize"),
                Arguments.of(new CatalogSearchCriteria(null, null, null, null, null, -1), "pageSize"),
                Arguments.of(new CatalogSearchCriteria(null, null, null, null, null, 101), "pageSize"),
                Arguments.of(new CatalogSearchCriteria(null, null, null, null, null, Integer.MAX_VALUE), "pageSize"));
    }

    @ParameterizedTest
    @MethodSource("invalidCriteria")
    void rejectsInvalidCriteriaBeforeRepositoryAccess(CatalogSearchCriteria criteria, String field) {
        assertThatThrownBy(() -> service.search(criteria)).isInstanceOfSatisfying(
                InvalidCatalogCriteriaException.class, error -> assertThat(error.field()).isEqualTo(field));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidDetailIdBeforeRepositoryAccess(Long id) {
        assertThatThrownBy(() -> service.getItem(id)).isInstanceOf(InvalidCatalogCriteriaException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void missingDetailHasExplicitApplicationError() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getItem(999L)).isInstanceOf(CatalogItemNotFoundException.class)
                .hasMessage("Catalog item not found: 999");
    }
}
