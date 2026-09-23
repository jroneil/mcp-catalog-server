package com.example.mcpcatalog.mcp.tools;

import com.example.mcpcatalog.catalog.application.CatalogItemNotFoundException;
import com.example.mcpcatalog.catalog.application.InvalidCatalogCriteriaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * MCP-boundary error sanitization.
 *
 * <p>Spring AI converts any exception thrown by a tool callback into an MCP tool error
 * whose text is {@code exception.getMessage()}. The application's own validation and
 * not-found messages are intentional and useful, but an unexpected failure (for example
 * a JDBC or connectivity error) can carry SQL text, connection strings, file paths or
 * other internal detail into that message.
 *
 * <p>This decorator therefore rethrows the application's defined errors unchanged and
 * replaces every other failure with {@link SanitizedToolFailureException}, whose message
 * is a fixed public-safe string. The original failure is logged server-side (without the
 * tool arguments, which may contain query data) and kept as the cause only.
 *
 * <p>This is an additional {@link ToolCallback} wrapper in the accepted callback chain,
 * not a replacement of the registration mechanism: tool definition, schema, argument
 * binding and result conversion remain the framework's, and no application exception or
 * tool contract changes.
 */
public final class SanitizingToolCallback implements ToolCallback {

	private static final Logger logger = LoggerFactory.getLogger(SanitizingToolCallback.class);

	private final ToolCallback delegate;

	public SanitizingToolCallback(ToolCallback delegate) {
		this.delegate = delegate;
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return this.delegate.getToolDefinition();
	}

	@Override
	public String call(String toolInput) {
		return call(toolInput, null);
	}

	@Override
	public String call(String toolInput, ToolContext toolContext) {
		try {
			return this.delegate.call(toolInput, toolContext);
		}
		catch (RuntimeException failure) {
			if (isApplicationError(failure)) {
				throw failure;
			}
			logger.error("MCP tool '{}' failed unexpectedly; a sanitized error was returned to the caller", toolName(),
					failure);
			throw new SanitizedToolFailureException(failure);
		}
	}

	/**
	 * Error reporting must not itself fail: a delegate that cannot supply its definition
	 * still produces the sanitized error rather than a secondary exception.
	 */
	private String toolName() {
		try {
			ToolDefinition definition = this.delegate.getToolDefinition();
			return definition != null && definition.name() != null ? definition.name() : "unknown";
		}
		catch (RuntimeException exception) {
			return "unknown";
		}
	}

	/**
	 * The application's defined, caller-facing errors. Their messages are fixed service
	 * text (and, for not-found, only the numeric identifier), so they are safe to expose.
	 */
	private static boolean isApplicationError(Throwable failure) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			if (current instanceof InvalidCatalogCriteriaException
					|| current instanceof CatalogItemNotFoundException) {
				return true;
			}
			if (current.getCause() == current) {
				break;
			}
		}
		return false;
	}

}
