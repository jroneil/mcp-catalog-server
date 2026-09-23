package com.example.mcpcatalog.rest;

/** Public error envelope; never carries exception diagnostics. */
public record RestError(int status, String error, String message, String path) {
}
