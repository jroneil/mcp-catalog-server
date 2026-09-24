package com.example.mcpcatalog.ai;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.example.mcpcatalog.ai.config.CatalogAssistantProperties;
import com.example.mcpcatalog.catalog.application.CatalogItemView;
import com.example.mcpcatalog.catalog.application.CatalogPage;
import com.example.mcpcatalog.catalog.application.CatalogSearchCriteria;
import com.example.mcpcatalog.catalog.application.CatalogService;
import com.example.mcpcatalog.catalog.application.InvalidCatalogCriteriaException;
import com.example.mcpcatalog.mcp.tools.SearchCatalogTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Slice 14 orchestration tests: a scripted chat model drives the real Spring AI
 * tool-calling pipeline over the real {@code search_catalog} capability adapter and a
 * mocked {@code CatalogService}. No provider is contacted.
 */
class CatalogAssistantServiceTest {

	private CatalogService catalogService;

	private ScriptedChatModel chatModel;

	private CatalogAssistantProperties properties;

	private CatalogAssistantService service;

	@BeforeEach
	void setUp() {
		this.catalogService = mock(CatalogService.class);
		this.chatModel = new ScriptedChatModel();
		this.properties = new CatalogAssistantProperties();
		this.properties.setTimeout(Duration.ofSeconds(5));
		this.service = new CatalogAssistantService(this.chatModel, new SearchCatalogTool(this.catalogService),
				this.properties, "qwen3-coder-next:latest");
	}

	@Test
	void groundsItemsAndPageMetadataInTheCapabilityResult() {
		when(this.catalogService.search(new CatalogSearchCriteria("SERVICE", true, new BigDecimal("200"), null, null,
				null)))
			.thenReturn(page());
		this.chatModel.then(toolCall("search_catalog", "{\"type\":\"SERVICE\",\"active\":true,\"maxPrice\":200}"))
			.then(text("Two active services cost at most 200."));

		CatalogAssistantResult result = this.service.ask("  Show me active service items under $200.  ");

		assertThat(result.answer()).isEqualTo("Two active services cost at most 200.");
		assertThat(result.capability()).isEqualTo("search_catalog");
		assertThat(result.items()).hasSize(2);
		assertThat(result.items().get(0).sku()).isEqualTo("SVC-101");
		assertThat(result.items().get(0).createdAt()).isEqualTo("2026-01-15T09:00:00Z");
		assertThat(result.page()).isZero();
		assertThat(result.pageSize()).isEqualTo(20);
		assertThat(result.totalItems()).isEqualTo(2);
		assertThat(result.totalPages()).isEqualTo(1);
		assertThat(result.provider()).isEqualTo("ollama");
		assertThat(result.model()).isEqualTo("qwen3-coder-next:latest");
		verify(this.catalogService).search(new CatalogSearchCriteria("SERVICE", true, new BigDecimal("200"), null, null,
				null));
	}

	@Test
	void reportsTheArgumentsTheModelActuallyUsed() {
		when(this.catalogService.search(any())).thenReturn(page());
		this.chatModel.then(toolCall("search_catalog", "{\"text\":\"network\",\"pageSize\":5}"))
			.then(text("Five matches."));

		CatalogAssistantResult result = this.service.ask("network items");

		assertThat(result.arguments()).containsEntry("text", "network").containsEntry("pageSize", 5);
		verify(this.catalogService).search(new CatalogSearchCriteria(null, null, null, "network", null, 5));
	}

	@Test
	void sendsTheTrimmedPromptToTheModel() {
		when(this.catalogService.search(any())).thenReturn(page());
		this.chatModel.then(toolCall("search_catalog", "{}")).then(text("All items."));

		this.service.ask("   list everything   ");

		List<org.springframework.ai.chat.messages.Message> instructions = this.chatModel.prompts.get(0)
			.getInstructions();
		assertThat(instructions.get(instructions.size() - 1).getText()).isEqualTo("list everything");
	}

	@Test
	void rejectsAModelTurnThatRequestsTwoCapabilityCallsWithoutExecutingAny() {
		AssistantMessage output = AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(
					new AssistantMessage.ToolCall("call-1", "function", "search_catalog", "{\"type\":\"SERVICE\"}"),
					new AssistantMessage.ToolCall("call-2", "function", "search_catalog", "{\"type\":\"PRODUCT\"}")))
			.build();
		this.chatModel.then(new ChatResponse(List.of(new Generation(output))));

		assertThatThrownBy(() -> this.service.ask("compare services and products"))
			.isInstanceOf(CatalogAssistantInternalException.class)
			.hasMessage(CatalogAssistantInternalException.MESSAGE);
		verify(this.catalogService, never()).search(any());
	}

	@Test
	void rejectsASecondCapabilityCallInALaterTurn() {
		when(this.catalogService.search(any())).thenReturn(page());
		this.chatModel.then(toolCall("search_catalog", "{\"type\":\"SERVICE\"}"))
			.then(toolCall("search_catalog", "{\"type\":\"PRODUCT\"}"));

		assertThatThrownBy(() -> this.service.ask("find services then products"))
			.isInstanceOf(CatalogAssistantInternalException.class);
		verify(this.catalogService, times(1)).search(any());
	}

	@Test
	void letsExistingServiceValidationRejectModelGeneratedArguments() {
		when(this.catalogService.search(any()))
			.thenThrow(new InvalidCatalogCriteriaException("pageSize", "Page size must be between 1 and 100"));
		this.chatModel.then(toolCall("search_catalog", "{\"pageSize\":101}"));

		assertThatThrownBy(() -> this.service.ask("everything at once"))
			.isInstanceOf(InvalidCatalogCriteriaException.class)
			.hasMessage("Page size must be between 1 and 100");
		// The rejected arguments are not handed back to the model for another attempt.
		assertThat(this.chatModel.prompts).hasSize(1);
	}

	@Test
	void treatsANonCatalogRequestAsUnsupportedIntent() {
		this.chatModel.then(text("I can only help with catalog search requests."));

		assertThatThrownBy(() -> this.service.ask("what is the weather today"))
			.isInstanceOf(UnsupportedCatalogAssistantIntentException.class)
			.hasMessage("This endpoint only supports catalog search requests.");
		verify(this.catalogService, never()).search(any());
	}

	@Test
	void rejectsMalformedModelToolOutput() {
		this.chatModel.then(toolCall("search_catalog", "{not-json")).then(text("Sorry, I could not search."));

		assertThatThrownBy(() -> this.service.ask("anything"))
			.isInstanceOf(CatalogAssistantInternalException.class)
			.hasMessage(CatalogAssistantInternalException.MESSAGE);
	}

	@Test
	void rejectsAnUnknownToolName() {
		this.chatModel.then(toolCall("delete_catalog_item", "{\"id\":16}"));

		assertThatThrownBy(() -> this.service.ask("delete item 16"))
			.isInstanceOf(CatalogAssistantInternalException.class)
			.hasMessage(CatalogAssistantInternalException.MESSAGE);
		verify(this.catalogService, never()).search(any());
	}

	@Test
	void rejectsAnEmptyAssistantAnswer() {
		when(this.catalogService.search(any())).thenReturn(page());
		this.chatModel.then(toolCall("search_catalog", "{}")).then(text("   "));

		assertThatThrownBy(() -> this.service.ask("list everything"))
			.isInstanceOf(CatalogAssistantInternalException.class)
			.hasMessage(CatalogAssistantInternalException.MESSAGE);
	}

	@Test
	void rejectsBlankAndOversizedPromptsWithoutContactingTheProvider() {
		assertThatThrownBy(() -> this.service.ask("   "))
			.isInstanceOf(InvalidCatalogAssistantRequestException.class)
			.hasMessage("Prompt must not be blank.");
		assertThatThrownBy(() -> this.service.ask(null))
			.isInstanceOf(InvalidCatalogAssistantRequestException.class);
		assertThatThrownBy(() -> this.service.ask("x".repeat(1001)))
			.isInstanceOf(InvalidCatalogAssistantRequestException.class)
			.hasMessage("Prompt must contain at most 1000 characters.");

		assertThat(this.chatModel.prompts).isEmpty();
		verify(this.catalogService, never()).search(any());
	}

	@Test
	void acceptsAPromptOfExactlyTheMaximumLength() {
		when(this.catalogService.search(any())).thenReturn(page());
		this.chatModel.then(toolCall("search_catalog", "{}")).then(text("All items."));

		this.service.ask("x".repeat(1000));

		assertThat(this.chatModel.prompts).hasSize(2);
	}

	@Test
	void reportsAnUnreachableProviderAsUnavailableWithoutLeakingDiagnostics() {
		this.chatModel.thenFail(new ResourceAccessException("I/O error on POST to Ollama",
				new ConnectException("Connection refused to /127.0.0.1:11434")));

		assertThatThrownBy(() -> this.service.ask("list everything"))
			.isInstanceOf(CatalogAssistantUnavailableException.class)
			.hasMessage("The catalog assistant is currently unavailable.")
			.hasMessageNotContaining("11434")
			.hasMessageNotContaining("I/O error");
	}

	@Test
	void reportsAProviderTimeoutAsATimeout() {
		this.properties.setTimeout(Duration.ofMillis(120));
		CatalogAssistantService bounded = new CatalogAssistantService(this.chatModel,
				new SearchCatalogTool(this.catalogService), this.properties, "qwen3-coder-next:latest");
		this.chatModel.thenSleep(Duration.ofSeconds(3));

		assertThatThrownBy(() -> bounded.ask("list everything"))
			.isInstanceOf(CatalogAssistantTimeoutException.class)
			.hasMessage("The catalog assistant timed out. Please try again.");
	}

	@Test
	void sanitizesUnexpectedProviderFailures() {
		this.chatModel.thenFail(new IllegalStateException("secret internal provider detail"));

		assertThatThrownBy(() -> this.service.ask("list everything"))
			.isInstanceOf(CatalogAssistantInternalException.class)
			.hasMessage(CatalogAssistantInternalException.MESSAGE)
			.hasMessageNotContaining("secret internal provider detail");
	}

	private static CatalogPage page() {
		OffsetDateTime created = OffsetDateTime.parse("2026-01-15T09:00:00Z");
		return new CatalogPage(
				List.of(new CatalogItemView(13, "SVC-101", "Network Audit", "SERVICE", "Audit", new BigDecimal("150.00"),
						true, created, created),
						new CatalogItemView(14, "SVC-102", "Firewall Review", "SERVICE", "Review",
								new BigDecimal("199.00"), true, created, created)),
				0, 20, 2, 1);
	}

	private static ChatResponse text(String text) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
	}

	private static ChatResponse toolCall(String name, String argumentsJson) {
		AssistantMessage output = AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", name, argumentsJson)))
			.build();
		return new ChatResponse(List.of(new Generation(output)));
	}

	/** Scripted chat model; no network access, no provider dependency. */
	private static final class ScriptedChatModel implements ChatModel {

		private final Deque<Object> scripted = new ArrayDeque<>();

		private final List<Prompt> prompts = new ArrayList<>();

		ScriptedChatModel then(ChatResponse response) {
			this.scripted.add(response);
			return this;
		}

		ScriptedChatModel thenFail(RuntimeException failure) {
			this.scripted.add(failure);
			return this;
		}

		ScriptedChatModel thenSleep(Duration duration) {
			this.scripted.add(duration);
			return this;
		}

		@Override
		public ChatResponse call(Prompt prompt) {
			this.prompts.add(prompt);
			Object next = this.scripted.poll();
			if (next == null) {
				throw new IllegalStateException("no scripted provider response remains");
			}
			if (next instanceof RuntimeException failure) {
				throw failure;
			}
			if (next instanceof Duration duration) {
				try {
					Thread.sleep(duration);
				}
				catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				}
				return text("late answer");
			}
			return (ChatResponse) next;
		}

		/** Must be tool-calling capable so ChatClient can attach the catalog capability. */
		@Override
		public ChatOptions getOptions() {
			return ToolCallingChatOptions.builder().build();
		}

	}

}
