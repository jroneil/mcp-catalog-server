package com.example.mcpcatalog.catalog.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Transport-independent application result, without persistence annotations. */
public record CatalogItemView(long id, String sku, String name, String type,
                              String description, BigDecimal price, boolean active,
                              OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
