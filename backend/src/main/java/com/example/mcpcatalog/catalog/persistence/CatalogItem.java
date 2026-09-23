package com.example.mcpcatalog.catalog.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Database row mapping; not a service DTO or transport contract. */
@Table(name = "catalog_item", schema = "public")
public record CatalogItem(
        @Id @Column("id") Long id,
        @Column("sku") String sku,
        @Column("name") String name,
        @Column("type") CatalogItemType type,
        @Column("description") String description,
        @Column("price") BigDecimal price,
        @Column("active") boolean active,
        @Column("created_at") OffsetDateTime createdAt,
        @Column("updated_at") OffsetDateTime updatedAt) {
}
