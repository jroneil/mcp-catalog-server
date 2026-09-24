package com.example.mcpcatalog.rest;

import com.example.mcpcatalog.ai.CatalogAssistantResult;
import com.example.mcpcatalog.ai.CatalogAssistantService;
import com.example.mcpcatalog.ai.CatalogAssistantUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Slice 14 (D5) HTTP adapter for the bounded catalog assistant.
 *
 * <p>This is a new endpoint and does not alter the accepted catalog search/detail
 * contract. It contains no orchestration: the assistant service owns the workflow, and the
 * endpoint reports unavailability when no provider is configured (AI disabled or an
 * unknown provider) rather than preventing application startup.
 */
@RestController
@RequestMapping("/api/v1/catalog/assistant")
public class CatalogAssistantController {

	private final ObjectProvider<CatalogAssistantService> assistant;

	public CatalogAssistantController(ObjectProvider<CatalogAssistantService> assistant) {
		this.assistant = assistant;
	}

	@PostMapping
	public CatalogAssistantResult ask(@RequestBody(required = false) CatalogAssistantRequest request) {
		CatalogAssistantService service = this.assistant.getIfAvailable();
		if (service == null) {
			throw new CatalogAssistantUnavailableException("The catalog assistant is not enabled.");
		}
		return service.ask(request == null ? null : request.prompt());
	}

	/** Request contract; validation and bounds belong to the assistant service. */
	public record CatalogAssistantRequest(String prompt) {
	}

}
