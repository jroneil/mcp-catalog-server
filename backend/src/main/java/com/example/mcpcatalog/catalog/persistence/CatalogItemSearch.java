package com.example.mcpcatalog.catalog.persistence;

import java.math.BigDecimal;
import java.util.List;

/** Internal persistence fragment. Callers supply service-validated filters and a required limit. */
public interface CatalogItemSearch {
    SearchRows search(CatalogItemType type, Boolean active, BigDecimal maxPrice,
                      String text, int limit, long offset);

    record SearchRows(List<CatalogItem> items, long total) {
        public SearchRows {
            items = List.copyOf(items);
        }
    }
}
