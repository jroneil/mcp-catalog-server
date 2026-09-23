package com.example.mcpcatalog.mcp.config;

import java.util.stream.Stream;

import com.example.mcpcatalog.mcp.tools.OptionalArgumentsToolCallback;
import com.example.mcpcatalog.mcp.tools.SearchCatalogTool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the shipped MCP tools through Spring AI's accepted
 * {@link ToolCallback} / {@link ToolCallbackProvider} bean mechanism. The
 * auto-configuration converts these beans into MCP tool specifications; no custom
 * registration or manual {@code SyncToolSpecification} wiring is used.
 *
 * <p>Tool name, description, input schema and argument binding stay
 * framework-generated from the annotated adapter methods, so this class only wires
 * beans and contains no catalog behavior.
 */
@Configuration(proxyBeanMethods = false)
public class McpToolConfiguration {

	@Bean
	ToolCallbackProvider catalogToolCallbackProvider(SearchCatalogTool searchCatalogTool) {
		ToolCallback[] callbacks = Stream.of(ToolCallbacks.from(searchCatalogTool))
			.map(OptionalArgumentsToolCallback::new)
			.toArray(ToolCallback[]::new);
		return ToolCallbackProvider.from(callbacks);
	}

}
