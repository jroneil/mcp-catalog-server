/**
 * MCP adapter configuration.
 *
 * <p>This package contains transport and wiring configuration only. It must not
 * contain catalog business logic or call repositories directly. Catalog tools
 * added in later slices live here as adapters over
 * {@code com.example.mcpcatalog.catalog.application.CatalogService}, and no
 * MCP-specific request or response type may leak into that service.
 */
package com.example.mcpcatalog.mcp.config;
