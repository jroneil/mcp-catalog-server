package com.example.mcpcatalog.rest;

import java.util.List;
import java.util.Map;

import com.example.mcpcatalog.ai.CatalogAssistantInternalException;
import com.example.mcpcatalog.ai.CatalogAssistantResult;
import com.example.mcpcatalog.ai.CatalogAssistantService;
import com.example.mcpcatalog.ai.CatalogAssistantTimeoutException;
import com.example.mcpcatalog.ai.CatalogAssistantUnavailableException;
import com.example.mcpcatalog.ai.InvalidCatalogAssistantRequestException;
import com.example.mcpcatalog.ai.UnsupportedCatalogAssistantIntentException;
import com.example.mcpcatalog.catalog.application.InvalidCatalogCriteriaException;
import com.example.mcpcatalog.mcp.tools.SearchCatalogItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice 14 (D5) HTTP contract for the catalog assistant, including safe error mapping and
 * the unavailable-when-not-configured behaviour.
 */
class CatalogAssistantHttpTest {

	private static final String ENDPOINT = "/api/v1/catalog/assistant";

	private static MockMvc mvc(CatalogAssistantService service) {
		DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
		if (service != null) {
			factory.registerSingleton("catalogAssistantService", service);
		}
		return MockMvcBuilders
			.standaloneSetup(new CatalogAssistantController(factory.getBeanProvider(CatalogAssistantService.class)))
			.setControllerAdvice(new CatalogRestExceptionHandler())
			.build();
	}

	private static CatalogAssistantService service() {
		return mock(CatalogAssistantService.class);
	}

	@Test
	void returnsTheDocumentedResponseShape() throws Exception {
		CatalogAssistantService service = service();
		when(service.ask(any())).thenReturn(new CatalogAssistantResult("Six active services match.",
				"search_catalog", Map.of("type", "SERVICE", "active", true, "maxPrice", 200),
				List.of(new SearchCatalogItem(13, "SVC-101", "Network Audit", "SERVICE", "Audit",
						new java.math.BigDecimal("150.00"), true, "2026-01-15T09:00:00Z", "2026-01-15T09:00:00Z")),
				0, 20, 6, 1, "ollama", "qwen3-coder-next:latest"));

		mvc(service).perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
			.content("{\"prompt\":\"Show me active service items under $200.\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.answer").value("Six active services match."))
			.andExpect(jsonPath("$.capability").value("search_catalog"))
			.andExpect(jsonPath("$.arguments.type").value("SERVICE"))
			.andExpect(jsonPath("$.arguments.maxPrice").value(200))
			.andExpect(jsonPath("$.items[0].id").value(13))
			.andExpect(jsonPath("$.items[0].sku").value("SVC-101"))
			.andExpect(jsonPath("$.items[0].price").value(150.00))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.pageSize").value(20))
			.andExpect(jsonPath("$.totalItems").value(6))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.provider").value("ollama"))
			.andExpect(jsonPath("$.model").value("qwen3-coder-next:latest"));
	}

	@Test
	void rejectsABlankPromptWithTheSafeErrorEnvelope() throws Exception {
		CatalogAssistantService service = service();
		when(service.ask(any())).thenThrow(new InvalidCatalogAssistantRequestException("Prompt must not be blank."));

		mvc(service).perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"  \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.error").value("Bad Request"))
			.andExpect(jsonPath("$.message").value("Prompt must not be blank."))
			.andExpect(jsonPath("$.path").value(ENDPOINT));
	}

	@Test
	void rejectsAnUnsupportedRequest() throws Exception {
		CatalogAssistantService service = service();
		when(service.ask(any())).thenThrow(new UnsupportedCatalogAssistantIntentException());

		mvc(service).perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
			.content("{\"prompt\":\"Write me a poem\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("This endpoint only supports catalog search requests."));
	}

	@Test
	void rejectsAMalformedRequestBody() throws Exception {
		mvc(service()).perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Malformed request body"));
	}

	@Test
	void mapsInvalidModelArgumentsToTheAcceptedValidationError() throws Exception {
		CatalogAssistantService service = service();
		when(service.ask(any()))
			.thenThrow(new InvalidCatalogCriteriaException("pageSize", "Page size must be between 1 and 100"));

		mvc(service).perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"all\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Page size must be between 1 and 100"));
	}

	@Test
	void reportsProviderUnavailabilityAs503() throws Exception {
		CatalogAssistantService service = service();
		when(service.ask(any())).thenThrow(new CatalogAssistantUnavailableException(
				"Connection refused to http://127.0.0.1:11434 and secret-token"));

		mvc(service).perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"all\"}"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.status").value(503));
	}

	@Test
	void reportsProviderTimeoutAs504() throws Exception {
		CatalogAssistantService service = service();
		when(service.ask(any())).thenThrow(new CatalogAssistantTimeoutException(new java.util.concurrent.TimeoutException()));

		mvc(service).perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"all\"}"))
			.andExpect(status().isGatewayTimeout())
			.andExpect(jsonPath("$.message").value("The catalog assistant timed out. Please try again."));
	}

	@Test
	void sanitizesInternalFailures() throws Exception {
		CatalogAssistantService service = service();
		when(service.ask(any())).thenThrow(
				new CatalogAssistantInternalException(new IllegalStateException("provider stack trace secret")));

		mvc(service).perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"all\"}"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.message").value("The catalog assistant could not complete the request."))
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
					org.hamcrest.Matchers.containsString("secret"))));
	}

	@Test
	void reportsUnavailabilityWhenNoProviderIsConfigured() throws Exception {
		mvc(null).perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"all\"}"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.status").value(503))
			.andExpect(jsonPath("$.message").value("The catalog assistant is not enabled."));
	}

}
