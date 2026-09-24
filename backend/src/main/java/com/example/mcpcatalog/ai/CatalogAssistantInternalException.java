package com.example.mcpcatalog.ai;

/**
 * The assistant could not complete the request for a reason that must not be exposed to
 * the caller (HTTP 500). Its message is a fixed, safe string; the original failure is
 * retained only as the cause for server-side diagnostics.
 */
public class CatalogAssistantInternalException extends RuntimeException {
    public static final String MESSAGE = "The catalog assistant could not complete the request.";

    public CatalogAssistantInternalException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
