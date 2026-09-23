package com.example.mcpcatalog.mcp.tools;

import java.util.List;

import com.example.mcpcatalog.catalog.application.CatalogPage;

/**
 * MCP-facing paged search result. Mirrors the application page metadata and items
 * so the MCP tool contract is explicit and can evolve independently of
 * {@code catalog.application}. Contains no persistence or MCP framework types.
 */
public record SearchCatalogResult(List<SearchCatalogItem> items, int page, int pageSize, long totalItems,
		long totalPages) {

	public SearchCatalogResult {
		items = List.copyOf(items);
	}

	static SearchCatalogResult from(CatalogPage page) {
		return new SearchCatalogResult(page.items().stream().map(SearchCatalogItem::from).toList(), page.page(),
				page.pageSize(), page.totalItems(), page.totalPages());
	}

}
