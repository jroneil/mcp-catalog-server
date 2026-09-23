package com.example.mcpcatalog.mcp.tools;

import com.example.mcpcatalog.catalog.application.CatalogService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP adapter for catalog item detail lookup.
 *
 * <p>This class performs input mapping and result mapping only. It owns no lookup
 * logic, validation or error translation: the identifier is passed to
 * {@link CatalogService#getItem(Long)} unchanged, and the resulting
 * {@code CatalogItemView} is mapped with the same shared MCP item shape used by
 * {@code search_catalog}, so field set, price scale and timestamp formatting stay
 * identical across both tools.
 *
 * <p>Missing items raise the application's {@code CatalogItemNotFoundException}
 * unchanged; the framework converts any thrown exception into an MCP tool error
 * ({@code isError: true}) carrying the exception message, so no item is ever
 * fabricated and no stack trace is returned. The adapter depends on
 * {@link CatalogService} only and never reaches the repository or persistence types.
 */
@Component
public class GetCatalogItemTool {

	private final CatalogService catalogService;

	public GetCatalogItemTool(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@Tool(name = "get_catalog_item",
			description = "Return exactly one catalog item by its catalog item identifier. "
					+ "Use this after search_catalog to read the full stored record for a specific item. "
					+ "Returns the item's id, sku, name, type, description, price, active flag, createdAt and "
					+ "updatedAt. The identifier must be a positive integer; if no item has that identifier the "
					+ "tool returns an error and no item data instead of inventing a record.")
	public SearchCatalogItem getCatalogItem(
			@ToolParam(description = "Catalog item identifier. Must be a positive integer, for example 16. "
					+ "Use search_catalog to discover identifiers.") Long id) {
		return SearchCatalogItem.from(catalogService.getItem(id));
	}

}
