package com.example.mcpcatalog.ai;

import com.example.mcpcatalog.ai.config.CatalogAssistantConfiguration;
import com.example.mcpcatalog.ai.config.CatalogAssistantProperties;
import com.example.mcpcatalog.catalog.application.CatalogService;
import com.example.mcpcatalog.mcp.tools.SearchCatalogTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Slice 14/15 (D7) provider configuration and startup semantics: each supported logical
 * provider selects its own chat model, and disabling the assistant or naming an unsupported
 * provider leaves normal startup intact without any hosted fallback.
 */
class CatalogAssistantConfigurationTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(CatalogAssistantConfiguration.class)
		.withBean(OllamaChatModel.class, () -> mock(OllamaChatModel.class))
		.withBean(OpenAiChatModel.class, () -> mock(OpenAiChatModel.class))
		.withBean(SearchCatalogTool.class, () -> new SearchCatalogTool(mock(CatalogService.class)));

	@Test
	void selectsTheLocalProviderByDefault() {
		this.runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(CatalogAssistantService.class);
		});
	}

	@Test
	void selectsTheLocalProviderWhenConfigured() {
		this.runner.withPropertyValues("catalog.ai.enabled=true", "catalog.ai.provider=ollama")
			.run(context -> assertThat(context).hasSingleBean(CatalogAssistantService.class));
	}

	@Test
	void selectsTheHostedProviderWhenConfigured() {
		this.runner.withPropertyValues("catalog.ai.enabled=true", "catalog.ai.provider=bailian")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(CatalogAssistantService.class);
			});
	}

	@Test
	void disabledAssistantStillStartsTheApplicationContext() {
		this.runner.withPropertyValues("catalog.ai.enabled=false").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(CatalogAssistantService.class);
		});
	}

	@Test
	void unsupportedProviderHasNoHostedFallbackAndStillStarts() {
		this.runner.withPropertyValues("catalog.ai.enabled=true", "catalog.ai.provider=anthropic")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(CatalogAssistantService.class);
			});
	}

	@Test
	void usesTheApprovedBoundsAndDefaults() {
		assertThat(CatalogAssistantProperties.MAX_PROMPT_LENGTH).isEqualTo(1000);
		CatalogAssistantProperties properties = new CatalogAssistantProperties();
		assertThat(properties.isEnabled()).isTrue();
		assertThat(properties.getProvider()).isEqualTo("ollama");
		assertThat(properties.getTimeout()).hasSeconds(60);
	}

}
