package com.example.mcpcatalog.catalog.application;

public class CatalogItemNotFoundException extends RuntimeException {
    public CatalogItemNotFoundException(long id) {
        super("Catalog item not found: " + id);
    }
}
