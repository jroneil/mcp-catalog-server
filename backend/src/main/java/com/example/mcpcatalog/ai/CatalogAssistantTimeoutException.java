package com.example.mcpcatalog.ai;

/** The provider did not respond within the configured bound (HTTP 504). */
public class CatalogAssistantTimeoutException extends RuntimeException {
    public static final String MESSAGE = "The catalog assistant timed out. Please try again.";

    public CatalogAssistantTimeoutException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
