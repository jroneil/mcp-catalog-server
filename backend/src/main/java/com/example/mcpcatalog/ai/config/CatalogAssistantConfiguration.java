package com.example.mcpcatalog.ai.config;

import com.example.mcpcatalog.ai.CatalogAssistantService;
import com.example.mcpcatalog.mcp.tools.SearchCatalogTool;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Slice 14/15 catalog assistant wiring.
 *
 * <p>Exactly one provider configuration is active, chosen by the logical
 * {@code catalog.ai.provider} value, and each selects its own Spring AI chat model:
 * {@code ollama} selects the local Ollama model and {@code bailian} selects the
 * OpenAI-compatible model that is pointed at Alibaba Cloud Model Studio (DashScope). The
 * concrete model types are injected so the selection is explicit and cannot silently fall
 * back to a different provider. {@code AiProviderEnvironmentPostProcessor} independently
 * guarantees that Spring AI activates only the matching chat auto-configuration.
 *
 * <p>The workflow itself is provider-neutral and unchanged: both providers produce the same
 * {@link CatalogAssistantService}. Disabling the assistant, or naming an unsupported
 * provider, leaves REST, MCP and catalog behaviour untouched and makes only the assistant
 * endpoint unavailable.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogAssistantProperties.class)
@ConditionalOnProperty(prefix = "catalog.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CatalogAssistantConfiguration {

	/** Local provider: the existing Ollama model and its configured model name. */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = "catalog.ai", name = "provider", havingValue = "ollama", matchIfMissing = true)
	static class OllamaProvider {

		@Bean
		CatalogAssistantService catalogAssistantService(OllamaChatModel chatModel, SearchCatalogTool searchTool,
				CatalogAssistantProperties properties,
				@Value("${spring.ai.ollama.chat.model:unknown}") String model) {
			return assistantService(chatModel, searchTool, properties, model);
		}

	}

	/** Hosted provider: OpenAI-compatible client configured for Bailian/DashScope. */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = "catalog.ai", name = "provider", havingValue = "bailian")
	static class BailianProvider {

		@Bean
		CatalogAssistantService catalogAssistantService(OpenAiChatModel chatModel, SearchCatalogTool searchTool,
				CatalogAssistantProperties properties,
				@Value("${spring.ai.openai.chat.model:unknown}") String model) {
			return assistantService(chatModel, searchTool, properties, model);
		}

	}

	private static CatalogAssistantService assistantService(ChatModel chatModel, SearchCatalogTool searchTool,
			CatalogAssistantProperties properties, String model) {
		return new CatalogAssistantService(chatModel, searchTool, properties, model);
	}

}
