package com.example.mcpcatalog.ai;

/**
 * The model produced no catalog capability invocation, so the request is not a
 * supported catalog search request (HTTP 400). The assistant never answers general
 * chat from model knowledge.
 */
public class UnsupportedCatalogAssistantIntentException extends RuntimeException {
    public static final String MESSAGE = "This endpoint only supports catalog search requests.";

    public UnsupportedCatalogAssistantIntentException() {
        super(MESSAGE);
    }
}
