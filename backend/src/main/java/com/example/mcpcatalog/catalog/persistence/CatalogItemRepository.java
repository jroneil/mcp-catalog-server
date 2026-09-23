package com.example.mcpcatalog.catalog.persistence;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/** Read-only persistence operations. Service policy remains in CatalogService. */
public interface CatalogItemRepository extends Repository<CatalogItem, Long>, CatalogItemSearch {
    Optional<CatalogItem> findById(Long id);

    Optional<CatalogItem> findBySku(String sku);

    long count();
}
