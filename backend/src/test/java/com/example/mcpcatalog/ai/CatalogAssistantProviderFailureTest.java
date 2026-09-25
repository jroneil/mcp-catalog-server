package com.example.mcpcatalog.ai;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.example.mcpcatalog.ai.config.CatalogAssistantProperties;
import com.example.mcpcatalog.catalog.application.CatalogService;
import com.example.mcpcatalog.mcp.tools.SearchCatalogTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Slice 15 (D8): hosted-provider failures must map to the same safe statuses as the local
 * provider, without leaking raw provider bodies, credential material, stack traces or
 * internal exception names. The blocking failures are exercised through Spring AI's
 * provider-neutral exception types; no network is used.
 */
class CatalogAssistantProviderFailureTest {

	private CatalogService catalogService;

	private FailingChatModel chatModel;

	private CatalogAssistantProperties properties;

	private CatalogAssistantService service;

	@BeforeEach
	void setUp() {
		this.catalogService = mock(CatalogService.class);
		this.chatModel = new FailingChatModel();
		this.properties = new CatalogAssistantProperties();
		this.properties.setProvider("bailian");
		this.properties.setTimeout(Duration.ofSeconds(5));
		this.service = new CatalogAssistantService(this.chatModel, new SearchCatalogTool(this.catalogService),
				this.properties, "qwen-plus");
	}

	@Test
	void mapsTransientAiFailuresToASanitized503() {
		this.chatModel.failWith(new TransientAiException(
				"429 Too Many Requests: {\"error\":{\"message\":\"quota exceeded for key sk-secret-value\"}}"));

		assertSanitizedUnavailable();
	}

	@Test
	void mapsNonTransientAiFailuresToASanitized503() {
		this.chatModel.failWith(new NonTransientAiException(
				"401 Unauthorized: Incorrect API key provided (Bearer sk-secret-value)"));

		assertSanitizedUnavailable();
	}

	@Test
	void mapsReactiveClientFailuresToASanitized503() {
		this.chatModel.failWith(new WebClientRequestException(new ConnectException("Connection refused"),
				HttpMethod.POST, URI.create("https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions"),
				HttpHeaders.EMPTY));

		assertSanitizedUnavailable();
	}

	@Test
	void mapsProviderTimeoutsToASanitized504() {
		this.chatModel.failWith(new HttpTimeoutException("request timed out after 60s against dashscope-intl"));

		assertThatThrownBy(() -> this.service.ask("Show me active service items under $200."))
			.isInstanceOf(CatalogAssistantTimeoutException.class)
			.hasMessage("The catalog assistant timed out. Please try again.")
			.hasMessageNotContaining("dashscope")
			.hasMessageNotContaining("60s");
	}

	@Test
	void sanitizesUnexpectedFailuresWithoutInternalDetail() {
		this.chatModel.failWith(new IllegalStateException("NonTransientAiException: raw provider payload sk-secret"));

		assertThatThrownBy(() -> this.service.ask("Show me active service items under $200."))
			.isInstanceOf(CatalogAssistantInternalException.class)
			.hasMessage("The catalog assistant could not complete the request.")
			.hasMessageNotContaining("NonTransientAiException")
			.hasMessageNotContaining("sk-secret")
			.hasMessageNotContaining("raw provider payload");
	}

	@Test
	void providerFailureNeverInvokesTheCatalogCapability() {
		this.chatModel.failWith(new NonTransientAiException("401 Unauthorized"));

		assertThatThrownBy(() -> this.service.ask("Show me active service items under $200."))
			.isInstanceOf(CatalogAssistantUnavailableException.class);
		verify(this.catalogService, never()).search(any());
	}

	private void assertSanitizedUnavailable() {
		assertThatThrownBy(() -> this.service.ask("Show me active service items under $200."))
			.isInstanceOf(CatalogAssistantUnavailableException.class)
			.hasMessage("The catalog assistant is currently unavailable.")
			.hasMessageNotContaining("sk-secret")
			.hasMessageNotContaining("Bearer")
			.hasMessageNotContaining("401")
			.hasMessageNotContaining("429")
			.hasMessageNotContaining("dashscope")
			.hasMessageNotContaining("TransientAiException");
	}

	/** Throws the configured failure on the first model call; never returns a result. */
	private static final class FailingChatModel implements ChatModel {

		private final BlockingQueue<Throwable> failures = new ArrayBlockingQueue<>(4);

		void failWith(Throwable failure) {
			this.failures.add(failure);
		}

		@Override
		public ChatResponse call(Prompt prompt) {
			Throwable failure = this.failures.poll();
			if (failure == null) {
				throw new IllegalStateException("no failure scripted");
			}
			// Checked client failures are surfaced the way a real client wraps them.
			throw failure instanceof RuntimeException runtime ? runtime : new RuntimeException(failure);
		}

		@Override
		public ChatOptions getOptions() {
			return ToolCallingChatOptions.builder().build();
		}

	}

}
