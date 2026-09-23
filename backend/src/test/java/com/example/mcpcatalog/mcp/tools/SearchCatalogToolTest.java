package com.example.mcpcatalog.mcp.tools;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.example.mcpcatalog.catalog.application.CatalogItemView;
import com.example.mcpcatalog.catalog.application.CatalogPage;
import com.example.mcpcatalog.catalog.application.CatalogSearchCriteria;
import com.example.mcpcatalog.catalog.application.CatalogService;
import com.example.mcpcatalog.catalog.application.InvalidCatalogCriteriaException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.JsonHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Slice 5 unit coverage: the MCP adapter maps input and output only and never
 * duplicates or replaces {@link CatalogService} behavior.
 */
class SearchCatalogToolTest {

	private static final OffsetDateTime CREATED = OffsetDateTime.parse("2026-01-15T09:00:00Z");

	private final CatalogService catalogService = mock(CatalogService.class);

	private final SearchCatalogTool tool = new SearchCatalogTool(this.catalogService);

	@Test
	void mapsEveryProvidedInputIntoTheApplicationCriteria() {
		when(this.catalogService.search(any())).thenReturn(emptyPage());

		this.tool.searchCatalog("SERVICE", true, new BigDecimal("199.99"), "network", 2, 5);

		ArgumentCaptor<CatalogSearchCriteria> criteria = ArgumentCaptor.forClass(CatalogSearchCriteria.class);
		verify(this.catalogService).search(criteria.capture());
		assertThat(criteria.getValue()).isEqualTo(
				new CatalogSearchCriteria("SERVICE", true, new BigDecimal("199.99"), "network", 2, 5));
	}

	@Test
	void passesOmittedInputsThroughAsNullSoTheServiceOwnsDefaults() {
		when(this.catalogService.search(any())).thenReturn(emptyPage());

		this.tool.searchCatalog(null, null, null, null, null, null);

		verify(this.catalogService).search(new CatalogSearchCriteria(null, null, null, null, null, null));
	}

	@Test
	void doesNotValidateFilterValuesItself() {
		when(this.catalogService.search(any())).thenReturn(emptyPage());

		this.tool.searchCatalog("NOT_A_TYPE", false, new BigDecimal("-5"), "x".repeat(500), -1, 100000);

		verify(this.catalogService)
			.search(new CatalogSearchCriteria("NOT_A_TYPE", false, new BigDecimal("-5"), "x".repeat(500), -1,
					100000));
	}

	@Test
	void mapsApplicationPageMetadataAndItemsOntoTheStructuredResult() {
		CatalogPage page = new CatalogPage(List.of(item()), 1, 5, 12, 3);
		when(this.catalogService.search(any())).thenReturn(page);

		SearchCatalogResult result = this.tool.searchCatalog(null, null, null, null, 1, 5);

		assertThat(result.page()).isEqualTo(1);
		assertThat(result.pageSize()).isEqualTo(5);
		assertThat(result.totalItems()).isEqualTo(12);
		assertThat(result.totalPages()).isEqualTo(3);
		assertThat(result.items()).containsExactly(new SearchCatalogItem(7L, "SVC-104", "Network Health Assessment",
				"SERVICE", "Review office network configuration.", new BigDecimal("199.00"), true,
				"2026-01-15T09:00:00Z", "2026-02-01T12:00:00Z"));
	}

	@Test
	void mapsAnEmptyPageToAnEmptyItemListWithTotalsPreserved() {
		when(this.catalogService.search(any())).thenReturn(new CatalogPage(List.of(), 9, 20, 24, 2));

		SearchCatalogResult result = this.tool.searchCatalog(null, null, null, null, 9, 20);

		assertThat(result.items()).isEmpty();
		assertThat(result.totalItems()).isEqualTo(24);
		assertThat(result.totalPages()).isEqualTo(2);
	}

	@Test
	void propagatesApplicationValidationErrorsInsteadOfReturningAFabricatedResult() {
		when(this.catalogService.search(any()))
			.thenThrow(new InvalidCatalogCriteriaException("pageSize", "Page size must be between 1 and 100"));

		assertThatThrownBy(() -> this.tool.searchCatalog(null, null, null, null, null, 101))
			.isInstanceOf(InvalidCatalogCriteriaException.class)
			.hasMessage("Page size must be between 1 and 100");
	}

	@Test
	void toolNameIsStableAndExactlySearchCatalog() {
		assertThat(definition().name()).isEqualTo("search_catalog");
	}

	@Test
	void descriptionIsModelReadableAndStatesTheContract() {
		String description = definition().description();

		assertThat(description).isNotBlank();
		assertThat(description).contains("catalog", "PRODUCT", "SERVICE", "pageSize", "totalItems", "totalPages");
	}

	@Test
	void inputSchemaDeclaresTheSixOptionalFiltersWithTypesAndDescriptions() {
		Map<String, Object> properties = properties();

		assertThat(properties).containsOnlyKeys("type", "active", "maxPrice", "text", "page", "pageSize");
		assertThat(propertyType("type")).isEqualTo("string");
		assertThat(propertyType("active")).isEqualTo("boolean");
		assertThat(propertyType("maxPrice")).isEqualTo("number");
		assertThat(propertyType("text")).isEqualTo("string");
		assertThat(propertyType("page")).isEqualTo("integer");
		assertThat(propertyType("pageSize")).isEqualTo("integer");
		assertThat(properties).allSatisfy((name, node) -> assertThat(property(name)).containsKey("description"));
		assertThat(properties.values()).allSatisfy(node -> assertThat(((Map<?, ?>) node).get("description"))
			.asString()
			.isNotBlank());
	}

	@Test
	void inputSchemaLeavesBoundsAndEnumsToTheCatalogService() {
		Map<String, Object> schema = schema();

		assertThat(schema).containsEntry("type", "object");
		assertThat(schema).containsEntry("$schema", "https://json-schema.org/draft/2020-12/schema");
		assertThat((List<?>) schema.get("required")).isEmpty();
		for (String name : List.of("type", "active", "maxPrice", "text", "page", "pageSize")) {
			assertThat(property(name)).as("property %s", name)
				.doesNotContainKeys("enum", "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum",
						"maxLength", "minLength");
		}
	}

	@Test
	void inputSchemaForbidsUnknownArguments() {
		assertThat(schema()).containsEntry("additionalProperties", false);
	}

	@Test
	void adapterDependsOnlyOnTheCatalogService() {
		assertThat(SearchCatalogTool.class.getDeclaredConstructors()).hasSize(1);
		assertThat(SearchCatalogTool.class.getDeclaredConstructors()[0].getParameterTypes())
			.containsExactly(CatalogService.class);
		assertThat(SearchCatalogTool.class.getDeclaredFields())
			.allSatisfy(field -> assertThat(field.getType()).isEqualTo(CatalogService.class));
	}

	@Test
	void mcpAdapterTypesNeverReferenceThePersistenceLayer() {
		List<Class<?>> adapterTypes = List.of(SearchCatalogTool.class, SearchCatalogResult.class,
				SearchCatalogItem.class, OptionalArgumentsToolCallback.class);

		for (Class<?> type : adapterTypes) {
			assertThat(referencedTypes(type)).as("persistence references in %s", type.getSimpleName())
				.noneMatch(name -> name.startsWith("com.example.mcpcatalog.catalog.persistence"));
		}
	}

	private static Stream<String> referencedTypes(Class<?> type) {
		Stream<String> fields = Stream.of(type.getDeclaredFields()).map(field -> field.getType().getName());
		Stream<String> constructors = Stream.of(type.getDeclaredConstructors())
			.flatMap(constructor -> Stream.of(constructor.getParameterTypes()))
			.map(Class::getName);
		Stream<String> methods = Stream.of(type.getDeclaredMethods())
			.flatMap(method -> Stream.concat(Stream.of(method.getReturnType()), Stream.of(method.getParameterTypes())))
			.map(Class::getName);
		return Stream.concat(fields, Stream.concat(constructors, methods));
	}

	private static CatalogPage emptyPage() {
		return new CatalogPage(List.of(), 0, 20, 0, 0);
	}

	private static CatalogItemView item() {
		return new CatalogItemView(7L, "SVC-104", "Network Health Assessment", "SERVICE",
				"Review office network configuration.", new BigDecimal("199.00"), true, CREATED,
				OffsetDateTime.parse("2026-02-01T12:00:00Z"));
	}

	private ToolDefinition definition() {
		return ToolCallbacks.from(this.tool)[0].getToolDefinition();
	}

	private Map<String, Object> schema() {
		return new JsonHelper().fromJsonToMap(definition().inputSchema());
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> properties() {
		return (Map<String, Object>) schema().get("properties");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> property(String name) {
		return (Map<String, Object>) properties().get(name);
	}

	private String propertyType(String name) {
		return (String) property(name).get("type");
	}

}
