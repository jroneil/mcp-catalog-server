package com.example.mcpcatalog.ai;

/** Invalid caller input for the catalog assistant (HTTP 400). */
public class InvalidCatalogAssistantRequestException extends RuntimeException {
    public InvalidCatalogAssistantRequestException(String message) {
        super(message);
    }
}
