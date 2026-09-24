package com.example.mcpcatalog.ai;

import com.example.mcpcatalog.ai.config.CatalogAssistantConfiguration;
import com.example.mcpcatalog.ai.config.CatalogAssistantProperties;
import com.example.mcpcatalog.catalog.application.CatalogService;
import com.example.mcpcatalog.mcp.tools.SearchCatalogTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Slice 14 (D7) configuration and startup semantics: the assistant only exists for the
 * approved local provider, and disabling it or naming another provider leaves normal
 * startup intact without any hosted fallback.
 */
class CatalogAssistantConfigurationTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(CatalogAssistantConfiguration.class)
		.withBean(ChatModel.class, () -> mock(ChatModel.class))
		.withBean(SearchCatalogTool.class, () -> new SearchCatalogTool(mock(CatalogService.class)));

	@Test
	void enablesTheLocalAssistantByDefaultWithoutCredentials() {
		this.runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(CatalogAssistantService.class);
		});
	}

	@Test
	void enablesTheLocalAssistantForTheApprovedOllamaProvider() {
		this.runner.withPropertyValues("catalog.ai.enabled=true", "catalog.ai.provider=ollama")
			.run(context -> assertThat(context).hasSingleBean(CatalogAssistantService.class));
	}

	@Test
	void disabledAssistantStillStartsTheApplicationContext() {
		this.runner.withPropertyValues("catalog.ai.enabled=false").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean(CatalogAssistantService.class);
		});
	}

	@Test
	void unknownProviderHasNoHostedFallbackAndStillStarts() {
		this.runner.withPropertyValues("catalog.ai.enabled=true", "catalog.ai.provider=openai")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(CatalogAssistantService.class);
			});
	}

	@Test
	void blankAndOversizedPromptBoundsAreTheApprovedValues() {
		assertThat(CatalogAssistantProperties.MAX_PROMPT_LENGTH).isEqualTo(1000);
		CatalogAssistantProperties properties = new CatalogAssistantProperties();
		assertThat(properties.isEnabled()).isTrue();
		assertThat(properties.getProvider()).isEqualTo("ollama");
		assertThat(properties.getTimeout()).hasSeconds(60);
	}

}
