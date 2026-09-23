package com.example.mcpcatalog.mcp.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Normalizes the MCP tool-call payload before delegating to the framework-provided
 * {@link ToolCallback}.
 *
 * <p>The MCP {@code tools/call} schema makes {@code arguments} optional, and Spring
 * AI's {@code McpToolUtils} forwards it verbatim to the callback. The framework's
 * {@code MethodToolCallback} then fails with {@code toolArguments must not be null}
 * when no arguments were sent, which would make the natural "no filters" call
 * unusable. An absent or JSON {@code null} payload is therefore treated as an empty
 * argument object. No other payload is modified, and tool definition, schema,
 * binding and error handling remain the framework's.
 */
public final class OptionalArgumentsToolCallback implements ToolCallback {

	private static final String EMPTY_ARGUMENTS = "{}";

	private final ToolCallback delegate;

	public OptionalArgumentsToolCallback(ToolCallback delegate) {
		this.delegate = delegate;
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return this.delegate.getToolDefinition();
	}

	@Override
	public String call(String toolInput) {
		return this.delegate.call(normalize(toolInput));
	}

	@Override
	public String call(String toolInput, ToolContext toolContext) {
		return this.delegate.call(normalize(toolInput), toolContext);
	}

	private static String normalize(String toolInput) {
		if (toolInput == null || toolInput.isBlank() || "null".equals(toolInput.strip())) {
			return EMPTY_ARGUMENTS;
		}
		return toolInput;
	}

}
