package com.example.mcpcatalog.ai;

/** The configured local provider is disabled, unknown or unreachable (HTTP 503). */
public class CatalogAssistantUnavailableException extends RuntimeException {
    public static final String MESSAGE = "The catalog assistant is currently unavailable.";

    public CatalogAssistantUnavailableException(String message) {
        super(message);
    }

    public CatalogAssistantUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
