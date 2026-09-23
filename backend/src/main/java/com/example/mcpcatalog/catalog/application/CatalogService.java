package com.example.mcpcatalog.catalog.application;

import java.math.BigDecimal;

import com.example.mcpcatalog.catalog.persistence.CatalogItem;
import com.example.mcpcatalog.catalog.persistence.CatalogItemRepository;
import com.example.mcpcatalog.catalog.persistence.CatalogItemType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_PAGE = 10_000;
    public static final int MAX_TEXT_LENGTH = 200;
    public static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");

    private final CatalogItemRepository repository;

    public CatalogService(CatalogItemRepository repository) {
        this.repository = repository;
    }

    /** Count and page share one database snapshot; there is no unpaged variant. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CatalogPage search(CatalogSearchCriteria criteria) {
        if (criteria == null) {
            criteria = new CatalogSearchCriteria(null, null, null, null, null, null);
        }
        int page = criteria.page() == null ? DEFAULT_PAGE : criteria.page();
        int size = criteria.pageSize() == null ? DEFAULT_PAGE_SIZE : criteria.pageSize();
        if (page < 0 || page > MAX_PAGE) {
            throw invalid("page", "Page must be between 0 and " + MAX_PAGE);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw invalid("pageSize", "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        CatalogItemType type = null;
        if (criteria.type() != null) {
            try {
                type = CatalogItemType.valueOf(criteria.type());
            } catch (IllegalArgumentException exception) {
                throw invalid("type", "Type must be PRODUCT or SERVICE");
            }
        }
        BigDecimal price = criteria.maxPrice();
        if (price != null && (price.signum() < 0 || price.compareTo(MAX_PRICE) > 0 || price.scale() > 2)) {
            throw invalid("maxPrice", "Maximum price must be between 0 and " + MAX_PRICE
                    + " with at most two decimal places");
        }
        String text = criteria.text();
        if (text != null) {
            if (text.length() > MAX_TEXT_LENGTH || text.indexOf('\0') >= 0) {
                throw invalid("text", "Text must contain at most " + MAX_TEXT_LENGTH + " characters and no NUL character");
            }
            text = text.strip();
            if (text.isEmpty()) {
                text = null;
            }
        }
        var result = repository.search(type, criteria.active(), price, text, size, (long) page * size);
        long pages = result.total() / size + (result.total() % size == 0 ? 0 : 1);
        return new CatalogPage(result.items().stream().map(CatalogService::toView).toList(),
                page, size, result.total(), pages);
    }

    @Transactional(readOnly = true)
    public CatalogItemView getItem(Long id) {
        if (id == null || id <= 0) {
            throw invalid("id", "Catalog item ID must be positive");
        }
        return repository.findById(id).map(CatalogService::toView)
                .orElseThrow(() -> new CatalogItemNotFoundException(id));
    }

    private static CatalogItemView toView(CatalogItem item) {
        return new CatalogItemView(item.id(), item.sku(), item.name(), item.type().name(),
                item.description(), item.price(), item.active(), item.createdAt(), item.updatedAt());
    }

    private static InvalidCatalogCriteriaException invalid(String field, String message) {
        return new InvalidCatalogCriteriaException(field, message);
    }
}
