package com.example.mcpcatalog.ai.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Slice 14 (D5/D7) application-level selection and bounds for the catalog assistant.
 *
 * <p>This is the workflow/provider selection surface only. The Ollama connection and
 * model stay in the starter's own {@code spring.ai.ollama.*} properties.
 */
@ConfigurationProperties(prefix = "catalog.ai")
public class CatalogAssistantProperties {

	/** Maximum accepted prompt length in UTF-16 code units (D5). */
	public static final int MAX_PROMPT_LENGTH = 1000;

	private boolean enabled = true;

	private String provider = "ollama";

	private Duration timeout = Duration.ofSeconds(60);

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getProvider() {
		return this.provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public Duration getTimeout() {
		return this.timeout;
	}

	public void setTimeout(Duration timeout) {
		if (timeout == null || timeout.isZero() || timeout.isNegative()) {
			throw new IllegalArgumentException("catalog.ai.timeout must be a positive duration");
		}
		this.timeout = timeout;
	}

}
