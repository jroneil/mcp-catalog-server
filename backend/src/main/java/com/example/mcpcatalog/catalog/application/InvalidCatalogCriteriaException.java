package com.example.mcpcatalog.catalog.application;

public class InvalidCatalogCriteriaException extends IllegalArgumentException {
    private final String field;

    public InvalidCatalogCriteriaException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
