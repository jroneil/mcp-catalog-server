package com.example.mcpcatalog.catalog.application;

import java.util.List;

public record CatalogPage(List<CatalogItemView> items, int page, int pageSize,
                          long totalItems, long totalPages) {
    public CatalogPage {
        items = List.copyOf(items);
    }
}
