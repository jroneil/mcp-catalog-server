package com.example.mcpcatalog.catalog.application;

import java.math.BigDecimal;

/** Optional application inputs; CatalogService validates and defaults them. */
public record CatalogSearchCriteria(String type, Boolean active, BigDecimal maxPrice,
                                    String text, Integer page, Integer pageSize) {
}
