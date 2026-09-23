package com.example.mcpcatalog.catalog.persistence;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/** Read-only persistence operations. Search and service policy belong to later slices. */
public interface CatalogItemRepository extends Repository<CatalogItem, Long> {
    Optional<CatalogItem> findById(Long id);

    Optional<CatalogItem> findBySku(String sku);

    long count();
}
