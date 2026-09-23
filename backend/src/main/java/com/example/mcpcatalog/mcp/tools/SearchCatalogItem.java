package com.example.mcpcatalog.mcp.tools;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.example.mcpcatalog.catalog.application.CatalogItemView;

/**
 * MCP-facing representation of one catalog item.
 *
 * <p>Timestamps are ISO-8601 UTC instants ({@code Instant.toString()}) so the
 * serialized contract does not depend on Jackson date/time module configuration or
 * on the database session offset. This type belongs to the MCP adapter; it is not
 * used by {@code catalog.application}.
 */
public record SearchCatalogItem(long id, String sku, String name, String type, String description,
		BigDecimal price, boolean active, String createdAt, String updatedAt) {

	static SearchCatalogItem from(CatalogItemView item) {
		return new SearchCatalogItem(item.id(), item.sku(), item.name(), item.type(), item.description(),
				item.price(), item.active(), instant(item.createdAt()), instant(item.updatedAt()));
	}

	private static String instant(OffsetDateTime value) {
		return value.toInstant().toString();
	}

}
