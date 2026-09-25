package com.example.mcpcatalog.ai;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import com.example.mcpcatalog.ai.config.CatalogAssistantProperties;
import com.example.mcpcatalog.catalog.application.InvalidCatalogCriteriaException;
import com.example.mcpcatalog.mcp.tools.SearchCatalogResult;
import com.example.mcpcatalog.mcp.tools.SearchCatalogTool;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.util.JsonHelper;
import org.springframework.web.client.RestClientException;

/**
 * Slice 14 bounded local catalog assistant (D4/D5).
 *
 * <p>Interprets one natural-language catalog search request with Spring AI's in-process
 * tool-calling path, permits exactly one catalog capability invocation, and returns a
 * result grounded in that capability's data. The model never supplies catalog records:
 * {@code items} and the page metadata come from the capability result, and the model only
 * contributes a summary of it.
 *
 * <p>Provider orchestration lives here, outside catalog business logic, MCP transport and
 * persistence. The tool itself is the existing {@link SearchCatalogTool} capability
 * adapter, so model-generated arguments reach {@code CatalogService} validation unchanged
 * and no MCP contract or new MCP tool is involved.
 */
public class CatalogAssistantService {

	private static final Logger logger = LoggerFactory.getLogger(CatalogAssistantService.class);

	private static final int WORKERS = 4;

	private static final String CAPABILITY = "search_catalog";

	private static final String SYSTEM_PROMPT = """
			You are the catalog search assistant for an internal product and service catalog.
			When the user asks to find, list, search or filter catalog items, call the search_catalog \
			tool exactly once with the best matching arguments and then summarise the returned items \
			using only that tool result. Never invent catalog data.
			If the request is not a catalog search request, do not call any tool and reply with a single \
			sentence saying that only catalog search requests are supported.""";

	/** Shared, stateless delegate; the per-request bound lives in the recorder instead. */
	private static final ToolCallingManager DELEGATE = ToolCallingManager.builder()
		.toolExecutionExceptionProcessor(DefaultToolExecutionExceptionProcessor.builder()
			.rethrowExceptions(List.of(InvalidCatalogCriteriaException.class))
			.build())
		.build();

	private final ChatModel chatModel;

	private final SearchCatalogTool searchTool;

	private final String provider;

	private final String model;

	private final Duration timeout;

	private final JsonHelper jsonHelper = new JsonHelper();

	private final ExecutorService executor;

	public CatalogAssistantService(ChatModel chatModel, SearchCatalogTool searchTool, CatalogAssistantProperties properties,
			String model) {
		this.chatModel = chatModel;
		this.searchTool = searchTool;
		this.provider = properties.getProvider();
		this.model = model;
		this.timeout = properties.getTimeout();
		this.executor = Executors.newFixedThreadPool(WORKERS, runnable -> {
			Thread thread = new Thread(runnable, "catalog-assistant");
			thread.setDaemon(true);
			return thread;
		});
		assertCapabilityAvailable(searchTool);
	}

	public CatalogAssistantResult ask(String prompt) {
		String request = validated(prompt);
		CatalogCapabilityRecorder recorder = new CatalogCapabilityRecorder();
		ChatResponse response = callProvider(request, recorder);
		if (!recorder.invoked()) {
			if (recorder.attempts() > 0) {
				throw new CatalogAssistantInternalException(
						new IllegalStateException("the catalog capability produced no result"));
			}
			throw new UnsupportedCatalogAssistantIntentException();
		}
		SearchCatalogResult page = readCapabilityResult(recorder.resultJson());
		return new CatalogAssistantResult(answerText(response), CAPABILITY, readArguments(recorder.argumentsJson()),
				page.items(), page.page(), page.pageSize(), page.totalItems(), page.totalPages(), this.provider, this.model);
	}

	@PreDestroy
	void shutdown() {
		this.executor.shutdownNow();
	}

	/** Fails fast at startup if the accepted capability adapter no longer exposes the tool. */
	private static void assertCapabilityAvailable(SearchCatalogTool searchTool) {
		if (Stream.of(ToolCallbacks.from(searchTool))
			.noneMatch(callback -> CAPABILITY.equals(callback.getToolDefinition().name()))) {
			throw new IllegalStateException("the " + CAPABILITY + " capability is not available");
		}
	}

	private static String validated(String prompt) {
		String trimmed = prompt == null ? "" : prompt.strip();
		if (trimmed.isEmpty()) {
			throw new InvalidCatalogAssistantRequestException("Prompt must not be blank.");
		}
		if (trimmed.length() > CatalogAssistantProperties.MAX_PROMPT_LENGTH) {
			throw new InvalidCatalogAssistantRequestException(
					"Prompt must contain at most " + CatalogAssistantProperties.MAX_PROMPT_LENGTH + " characters.");
		}
		return trimmed;
	}

	private ChatResponse callProvider(String prompt, CatalogCapabilityRecorder recorder) {
		Future<ChatResponse> pending = this.executor.submit(() -> assistantClient(recorder).prompt()
			.user(prompt)
			.tools(this.searchTool)
			.call()
			.chatResponse());
		try {
			return pending.get(this.timeout.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException timeout) {
			pending.cancel(true);
			logger.warn("Catalog assistant provider timed out after {}", this.timeout);
			throw new CatalogAssistantTimeoutException(timeout);
		}
		catch (InterruptedException interrupted) {
			pending.cancel(true);
			Thread.currentThread().interrupt();
			throw new CatalogAssistantInternalException(interrupted);
		}
		catch (ExecutionException failure) {
			throw classify(failure.getCause());
		}
	}

	/**
	 * A dedicated client per request keeps the single-call bound and the capability
	 * recording scoped to one request instead of the whole application. Supplying our own
	 * {@link ToolCallingAdvisor} also makes Spring AI skip its automatic one, so no other
	 * tool-calling configuration can widen the bound.
	 */
	private ChatClient assistantClient(CatalogCapabilityRecorder recorder) {
		return ChatClient.builder(this.chatModel)
			.defaultAdvisors(ToolCallingAdvisor.builder()
				.toolCallingManager(new RecordingToolCallingManager(DELEGATE, recorder))
				.build())
			.defaultSystem(SYSTEM_PROMPT)
			.build();
	}

	/**
	 * Service validation errors keep their own type (and therefore the same safe 400 and
	 * message as the REST search endpoint). Provider connectivity, authentication, quota and
	 * other provider-side failures become 503, timeouts 504, and everything else a sanitized
	 * 500. The classification is provider-neutral: alongside the blocking clients' IO/HTTP
	 * failures it recognises Spring AI's own transient and non-transient AI exceptions, so
	 * the local and hosted providers map to the same statuses.
	 */
	private static RuntimeException classify(Throwable failure) {
		if (failure instanceof InvalidCatalogCriteriaException invalid) {
			return invalid;
		}
		if (failure instanceof CatalogAssistantInternalException || failure instanceof CatalogAssistantTimeoutException
				|| failure instanceof CatalogAssistantUnavailableException
				|| failure instanceof UnsupportedCatalogAssistantIntentException
				|| failure instanceof InvalidCatalogAssistantRequestException) {
			return (RuntimeException) failure;
		}
		logger.warn("Catalog assistant provider call failed", failure);
		if (hasCause(failure, SocketTimeoutException.class, HttpTimeoutException.class, TimeoutException.class)
				|| hasTimeoutType(failure)) {
			return new CatalogAssistantTimeoutException(failure);
		}
		if (hasCause(failure, IOException.class, RestClientException.class, TransientAiException.class,
				NonTransientAiException.class) || isReactiveClientFailure(failure)) {
			return new CatalogAssistantUnavailableException(CatalogAssistantUnavailableException.MESSAGE, failure);
		}
		return new CatalogAssistantInternalException(failure);
	}

	/**
	 * Recognises provider-neutral timeout types, including the reactive/Netty timeouts that
	 * are not JDK {@link TimeoutException}s, without depending on those client types.
	 */
	private static boolean hasTimeoutType(Throwable failure) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			for (Class<?> type = current.getClass(); type != null; type = type.getSuperclass()) {
				if (type.getSimpleName().contains("Timeout")) {
					return true;
				}
			}
			if (current.getCause() == current) {
				break;
			}
		}
		return false;
	}

	/**
	 * Recognises the reactive HTTP client failures the OpenAI-compatible module can raise
	 * without this adapter depending on WebFlux types directly.
	 */
	private static boolean isReactiveClientFailure(Throwable failure) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			for (Class<?> type = current.getClass(); type != null; type = type.getSuperclass()) {
				if (type.getName().startsWith("org.springframework.web.reactive.function.client.WebClient")) {
					return true;
				}
			}
			if (current.getCause() == current) {
				break;
			}
		}
		return false;
	}

	@SafeVarargs
	private static boolean hasCause(Throwable failure, Class<? extends Throwable>... types) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			for (Class<? extends Throwable> type : types) {
				if (type.isInstance(current)) {
					return true;
				}
			}
			if (current.getCause() == current) {
				break;
			}
		}
		return false;
	}

	private SearchCatalogResult readCapabilityResult(String json) {
		try {
			SearchCatalogResult result = this.jsonHelper.fromJson(json, SearchCatalogResult.class);
			if (result == null) {
				throw new IllegalStateException("the catalog capability returned no result");
			}
			return result;
		}
		catch (RuntimeException failure) {
			throw new CatalogAssistantInternalException(failure);
		}
	}

	private Map<String, Object> readArguments(String json) {
		try {
			return this.jsonHelper.fromJsonToMap(json == null || json.isBlank() ? "{}" : json);
		}
		catch (RuntimeException failure) {
			throw new CatalogAssistantInternalException(failure);
		}
	}

	private static String answerText(ChatResponse response) {
		if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
			throw new CatalogAssistantInternalException(new IllegalStateException("no assistant response"));
		}
		String text = response.getResult().getOutput().getText();
		if (text == null || text.isBlank()) {
			throw new CatalogAssistantInternalException(new IllegalStateException("empty assistant answer"));
		}
		return text.strip();
	}

}
