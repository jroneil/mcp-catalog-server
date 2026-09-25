package com.example.mcpcatalog.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 15 (D7): the logical provider selection is mapped onto Spring AI's model switch so
 * that exactly one chat model can ever be active, including for unset or unknown values.
 */
class AiProviderEnvironmentPostProcessorTest {

	private final AiProviderEnvironmentPostProcessor postProcessor = new AiProviderEnvironmentPostProcessor();

	@Test
	void localProviderSelectsTheOllamaChatModel() {
		assertThat(switchFor("catalog.ai.provider", "ollama")).isEqualTo("ollama");
	}

	@Test
	void hostedProviderSelectsTheOpenAiCompatibleChatModel() {
		assertThat(switchFor("catalog.ai.provider", "bailian")).isEqualTo("openai");
	}

	@Test
	void defaultsToTheLocalProviderWhenUnset() {
		MockEnvironment environment = new MockEnvironment();
		this.postProcessor.postProcessEnvironment(environment, null);
		assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("ollama");
	}

	@Test
	void normalisesCaseAndWhitespace() {
		assertThat(switchFor("catalog.ai.provider", "  BAILIAN  ")).isEqualTo("openai");
		assertThat(switchFor("catalog.ai.provider", " Ollama ")).isEqualTo("ollama");
	}

	@Test
	void unknownProviderKeepsASingleLocalChatModel() {
		assertThat(switchFor("catalog.ai.provider", "anthropic")).isEqualTo("ollama");
	}

	@Test
	void readsTheResolvedProviderRegardlessOfSource() {
		// The logical property may come from any property source, not just AI_PROVIDER.
		assertThat(switchFor("AI_PROVIDER", "bailian")).isEqualTo("ollama");
		assertThat(switchFor("catalog.ai.provider", "bailian")).isEqualTo("openai");
	}

	private String switchFor(String key, String value) {
		MockEnvironment environment = new MockEnvironment().withProperty(key, value);
		this.postProcessor.postProcessEnvironment(environment, null);
		return environment.getProperty("spring.ai.model.chat");
	}

}
