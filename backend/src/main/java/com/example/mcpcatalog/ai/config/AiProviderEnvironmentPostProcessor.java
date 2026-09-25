package com.example.mcpcatalog.ai.config;

import java.util.Locale;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Slice 15 (D7): maps the single logical provider selection onto Spring AI's model switch so
 * that exactly one ChatModel bean is created.
 *
 * <p>Spring AI's Ollama and OpenAI chat auto-configurations both declare
 * {@code matchIfMissing = true} for {@code spring.ai.model.chat}, so with both starters on
 * the classpath and the switch unset they would create two competing ChatModel beans. This
 * post-processor derives the switch from the logical provider instead:
 * {@code ollama} selects the Ollama model and {@code bailian} selects the OpenAI-compatible
 * model configured for DashScope. Selection stays configuration-only; there is no hot
 * switching, no provider-selection UI and no fallback.
 *
 * <p>It runs last so that configuration data (and therefore {@code catalog.ai.provider},
 * which may come from any property source) is already resolved. Only the two supported
 * providers select a hosted or local model; any other value keeps the local default while
 * the assistant itself stays disabled, so an unknown provider cannot reach a provider.
 */
public class AiProviderEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	static final String PROVIDER_PROPERTY = "catalog.ai.provider";

	static final String MODEL_SWITCH_PROPERTY = "spring.ai.model.chat";

	static final String DEFAULT_PROVIDER = "ollama";

	static final String OLLAMA_CHAT_MODEL = "ollama";

	/** Logical provider name for Bailian; the OpenAI module is only the protocol client. */
	static final String BAILIAN_PROVIDER = "bailian";

	static final String OPENAI_COMPATIBLE_CHAT_MODEL = "openai";

	private static final String PROPERTY_SOURCE_NAME = "catalog-ai-provider-selection";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		environment.getPropertySources()
			.addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME,
					Map.of(MODEL_SWITCH_PROPERTY, chatModelFor(logicalProvider(environment)))));
	}

	private static String logicalProvider(ConfigurableEnvironment environment) {
		String provider = environment.getProperty(PROVIDER_PROPERTY, DEFAULT_PROVIDER);
		return provider == null ? DEFAULT_PROVIDER : provider.trim().toLowerCase(Locale.ROOT);
	}

	private static String chatModelFor(String logicalProvider) {
		return BAILIAN_PROVIDER.equals(logicalProvider) ? OPENAI_COMPATIBLE_CHAT_MODEL : OLLAMA_CHAT_MODEL;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

}
