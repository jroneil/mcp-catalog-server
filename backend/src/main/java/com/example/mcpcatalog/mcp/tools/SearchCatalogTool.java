package com.example.mcpcatalog.mcp.tools;

import java.math.BigDecimal;

import com.example.mcpcatalog.catalog.application.CatalogSearchCriteria;
import com.example.mcpcatalog.catalog.application.CatalogService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP adapter for catalog search.
 *
 * <p>This class performs input mapping and result mapping only. It owns no
 * defaults, bounds, filtering, ordering or pagination arithmetic: every value is
 * passed to {@link CatalogService} exactly as received (an omitted argument stays
 * {@code null}) and the returned application page is mapped to the MCP result
 * shape. Validation failures raised by the service propagate unchanged, so the MCP
 * error text is the service's own message.
 *
 * <p>The adapter depends on {@link CatalogService} only. It must never reach the
 * repository or persistence types, and no MCP type may be added to
 * {@code catalog.application}.
 */
@Component
public class SearchCatalogTool {

	private final CatalogService catalogService;

	public SearchCatalogTool(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@Tool(name = "search_catalog",
			description = "Search the product and service catalog and return one page of matching items. "
					+ "All parameters are optional and combine as AND filters; omit a parameter to leave it "
					+ "unconstrained. 'type' selects PRODUCT or SERVICE, 'active' selects active or inactive items, "
					+ "'maxPrice' is an inclusive price ceiling, and 'text' is a case-insensitive substring matched "
					+ "against SKU, name or description. Results are ordered by catalog item id ascending and "
					+ "paginated with 'page' (zero-based, default 0) and 'pageSize' (default 20, maximum 100); the "
					+ "response reports totalItems and totalPages so further pages can be requested. Invalid filter "
					+ "values are rejected with an error instead of returning partial or unbounded results.")
	public SearchCatalogResult searchCatalog(
			@ToolParam(required = false,
					description = "Optional item type filter. Must be exactly PRODUCT or SERVICE. "
							+ "Omit to include both types.") String type,
			@ToolParam(required = false,
					description = "Optional active-status filter. true returns only active items, "
							+ "false returns only inactive items. Omit to include both.") Boolean active,
			@ToolParam(required = false,
					description = "Optional inclusive maximum price, for example 200 or 199.99. Must be at "
							+ "least 0, at most 9999999999.99, and have no more than two decimal places. "
							+ "Omit for no price limit.") BigDecimal maxPrice,
			@ToolParam(required = false,
					description = "Optional literal text matched case-insensitively anywhere in the SKU, name "
							+ "or description. At most 200 characters. Omit for no text filter.") String text,
			@ToolParam(required = false,
					description = "Optional zero-based page number. Defaults to 0. Must be between 0 and 10000.")
			Integer page,
			@ToolParam(required = false,
					description = "Optional number of items per page. Defaults to 20. Must be between 1 and 100.")
			Integer pageSize) {
		return SearchCatalogResult.from(catalogService
			.search(new CatalogSearchCriteria(type, active, maxPrice, text, page, pageSize)));
	}

}
