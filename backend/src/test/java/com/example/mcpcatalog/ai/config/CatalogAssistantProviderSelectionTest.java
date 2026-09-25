package com.example.mcpcatalog.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 15 (D7): with both provider starters on the classpath, the selection must leave
 * exactly one active chat model — no competing ChatModel beans — and it must not require a
 * hosted credential to start. Uses Spring AI's real chat auto-configurations.
 */
class CatalogAssistantProviderSelectionTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(OllamaApiAutoConfiguration.class, OllamaChatAutoConfiguration.class,
				OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
		.withInitializer(context -> new AiProviderEnvironmentPostProcessor()
			.postProcessEnvironment(context.getEnvironment(), null));

	@Test
	void localProviderActivatesOnlyTheOllamaChatModel() {
		this.runner.withPropertyValues("catalog.ai.provider=ollama", "spring.ai.ollama.chat.model=qwen3-coder-next:latest")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(ChatModel.class);
				assertThat(context).hasSingleBean(OllamaChatModel.class);
				assertThat(context).doesNotHaveBean(OpenAiChatModel.class);
			});
	}

	@Test
	void hostedProviderActivatesOnlyTheOpenAiCompatibleChatModelAndStartsWithoutCredentials() {
		this.runner
			.withPropertyValues("catalog.ai.provider=bailian", "spring.ai.openai.api-key=",
					"spring.ai.openai.base-url=https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
					"spring.ai.openai.chat.model=qwen-plus")
			.run(context -> {
				// A missing credential must not prevent startup.
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(ChatModel.class);
				assertThat(context).hasSingleBean(OpenAiChatModel.class);
				assertThat(context).doesNotHaveBean(OllamaChatModel.class);
			});
	}

	@Test
	void switchingProvidersIsConfigurationOnly() {
		this.runner.withPropertyValues("catalog.ai.provider=bailian", "spring.ai.openai.api-key=unused-placeholder")
			.run(context -> assertThat(context).hasSingleBean(OpenAiChatModel.class));
		this.runner.withPropertyValues("catalog.ai.provider=ollama")
			.run(context -> assertThat(context).hasSingleBean(OllamaChatModel.class));
	}

}
