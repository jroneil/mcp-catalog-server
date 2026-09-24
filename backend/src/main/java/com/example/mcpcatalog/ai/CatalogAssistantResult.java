package com.example.mcpcatalog.ai;

import java.util.List;
import java.util.Map;

import com.example.mcpcatalog.mcp.tools.SearchCatalogItem;

/**
 * Slice 14 (D5) catalog assistant response.
 *
 * <p>{@code items} and the page metadata are taken from the catalog capability result,
 * never from the model; {@code arguments} are the arguments actually used for the
 * invocation. The model contributes only {@code answer}, which summarises that result.
 * {@code provider} and {@code model} identify the configured execution source.
 */
public record CatalogAssistantResult(String answer, String capability, Map<String, Object> arguments,
		List<SearchCatalogItem> items, int page, int pageSize, long totalItems, long totalPages, String provider,
		String model) {
}
