package com.example.mcpcatalog.rest;

import com.example.mcpcatalog.catalog.application.CatalogItemNotFoundException;
import com.example.mcpcatalog.catalog.application.InvalidCatalogCriteriaException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
