package com.example.mcpcatalog.rest;

import com.example.mcpcatalog.ai.CatalogAssistantInternalException;
import com.example.mcpcatalog.ai.CatalogAssistantTimeoutException;
import com.example.mcpcatalog.ai.CatalogAssistantUnavailableException;
import com.example.mcpcatalog.ai.InvalidCatalogAssistantRequestException;
import com.example.mcpcatalog.ai.UnsupportedCatalogAssistantIntentException;
import com.example.mcpcatalog.catalog.application.CatalogItemNotFoundException;
import com.example.mcpcatalog.catalog.application.InvalidCatalogCriteriaException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Scoped to REST so MCP's accepted error contract remains untouched. */
@RestControllerAdvice(basePackageClasses = CatalogController.class)
public class CatalogRestExceptionHandler {
    @ExceptionHandler(InvalidCatalogCriteriaException.class)
    public ResponseEntity<RestError> invalid(InvalidCatalogCriteriaException exception,
                                              HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RestError> malformed(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Malformed request parameter", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RestError> unreadable(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Malformed request body", request);
    }

    // Slice 14 catalog assistant. Provider/model diagnostics are never included: only the
    // fixed messages defined by these exception types are returned.
    @ExceptionHandler({ InvalidCatalogAssistantRequestException.class, UnsupportedCatalogAssistantIntentException.class })
    public ResponseEntity<RestError> invalidAssistantRequest(RuntimeException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(CatalogAssistantUnavailableException.class)
    public ResponseEntity<RestError> assistantUnavailable(CatalogAssistantUnavailableException exception,
                                                           HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), request);
    }

    @ExceptionHandler(CatalogAssistantTimeoutException.class)
    public ResponseEntity<RestError> assistantTimeout(CatalogAssistantTimeoutException exception,
                                                       HttpServletRequest request) {
        return error(HttpStatus.GATEWAY_TIMEOUT, exception.getMessage(), request);
    }

    @ExceptionHandler(CatalogAssistantInternalException.class)
    public ResponseEntity<RestError> assistantFailure(CatalogAssistantInternalException exception,
                                                       HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage(), request);
    }

    @ExceptionHandler(CatalogItemNotFoundException.class)
    public ResponseEntity<RestError> missing(CatalogItemNotFoundException exception,
                                              HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestError> unexpected(HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
    }

    private ResponseEntity<RestError> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new RestError(status.value(), status.getReasonPhrase(),
                message, request.getRequestURI()));
    }
}
