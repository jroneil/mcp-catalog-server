package com.example.mcpcatalog.mcp.tools;

/**
 * MCP-boundary failure used when a tool fails for a reason the application does not
 * define as a caller-visible error.
 *
 * <p>Its message is the only text an MCP client sees for such a failure, so it never
 * contains SQL text, connection strings, file paths, credentials, stack frames or other
 * internal implementation details. The original failure is retained as the cause for
 * server-side diagnostics only and is never serialized into the tool result.
 */
public class SanitizedToolFailureException extends RuntimeException {

	/** Stable, caller-visible replacement message for unexpected internal failures. */
	public static final String MESSAGE = "The tool failed due to an internal server error and returned no data.";

	public SanitizedToolFailureException(Throwable cause) {
		super(MESSAGE, cause);
	}

}
