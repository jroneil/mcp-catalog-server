package com.example.mcpcatalog.ai.config;

import com.example.mcpcatalog.ai.CatalogAssistantService;
import com.example.mcpcatalog.mcp.tools.SearchCatalogTool;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Slice 14 (D4–D7) catalog assistant wiring.
 *
 * <p>Active only when the assistant is enabled <em>and</em> the configured provider is
 * {@code ollama}; any other state leaves REST, MCP and catalog behavior untouched and makes
 * only the assistant endpoint unavailable. No hosted provider is configured and no
 * automatic fallback exists. The application never contacts Ollama during startup, so
 * normal startup and REST/MCP/catalog serving do not depend on the provider being present.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogAssistantProperties.class)
@ConditionalOnProperty(prefix = "catalog.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "catalog.ai", name = "provider", havingValue = "ollama", matchIfMissing = true)
public class CatalogAssistantConfiguration {

	/**
	 * The workflow takes the accepted {@code search_catalog} capability adapter and the
	 * configured chat model. The adapter is used directly, so the assistant reuses the
	 * existing tool definition and service path instead of duplicating them or adding a
	 * new MCP tool.
	 */
	@Bean
	CatalogAssistantService catalogAssistantService(ChatModel chatModel, SearchCatalogTool searchTool,
			CatalogAssistantProperties properties, @Value("${spring.ai.ollama.chat.model:unknown}") String model) {
		return new CatalogAssistantService(chatModel, searchTool, properties, model);
	}

}
