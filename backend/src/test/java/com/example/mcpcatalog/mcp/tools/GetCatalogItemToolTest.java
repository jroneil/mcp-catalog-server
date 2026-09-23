package com.example.mcpcatalog.mcp.tools;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.example.mcpcatalog.catalog.application.CatalogItemNotFoundException;
import com.example.mcpcatalog.catalog.application.CatalogItemView;
import com.example.mcpcatalog.catalog.application.CatalogService;
import com.example.mcpcatalog.catalog.application.InvalidCatalogCriteriaException;
import org.junit.jupiter.api.Test;
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
 * Slice 6 unit coverage: the detail adapter maps input and output only, delegates
 * lookup to {@link CatalogService}, and never fabricates a missing item.
 */
class GetCatalogItemToolTest {

	private final CatalogService catalogService = mock(CatalogService.class);

	private final GetCatalogItemTool tool = new GetCatalogItemTool(this.catalogService);

	@Test
	void delegatesTheIdentifierToCatalogServiceUnchanged() {
		when(this.catalogService.getItem(16L)).thenReturn(seededItem());

		this.tool.getCatalogItem(16L);

		verify(this.catalogService).getItem(16L);
	}

	@Test
	void doesNotValidateTheIdentifierItself() {
		when(this.catalogService.getItem(any())).thenReturn(seededItem());

		this.tool.getCatalogItem(0L);
		this.tool.getCatalogItem(-5L);
		this.tool.getCatalogItem(null);

		verify(this.catalogService).getItem(0L);
		verify(this.catalogService).getItem(-5L);
		verify(this.catalogService).getItem(null);
	}

	@Test
	void mapsTheApplicationItemOntoTheSharedMcpItemShape() {
		when(this.catalogService.getItem(16L)).thenReturn(seededItem());

		assertThat(this.tool.getCatalogItem(16L))
			.isEqualTo(new SearchCatalogItem(16L, "SVC-104", "Network Health Assessment", "SERVICE",
					"Review office network configuration and provide a prioritized findings report.",
					new BigDecimal("199.00"), true, "2026-01-15T09:00:00Z", "2026-01-15T09:00:00Z"));
	}

	@Test
	void formatsTimestampsAsUtcInstantsConsistentlyWithSearchCatalog() {
		when(this.catalogService.getItem(16L))
			.thenReturn(new CatalogItemView(16L, "SVC-104", "Network Health Assessment", "SERVICE", "description",
					new BigDecimal("199.00"), true, OffsetDateTime.parse("2026-01-15T14:30:00+05:30"),
					OffsetDateTime.parse("2026-01-16T00:00:00+02:00")));

		SearchCatalogItem item = this.tool.getCatalogItem(16L);

		assertThat(item.createdAt()).isEqualTo("2026-01-15T09:00:00Z");
		assertThat(item.updatedAt()).isEqualTo("2026-01-15T22:00:00Z");
	}

	@Test
	void propagatesTheApplicationNotFoundErrorWithoutInventingAnItem() {
		when(this.catalogService.getItem(99999L)).thenThrow(new CatalogItemNotFoundException(99999L));

		assertThatThrownBy(() -> this.tool.getCatalogItem(99999L))
			.isInstanceOf(CatalogItemNotFoundException.class)
			.hasMessage("Catalog item not found: 99999");
	}

	@Test
	void propagatesTheApplicationInvalidIdentifierError() {
		when(this.catalogService.getItem(0L))
			.thenThrow(new InvalidCatalogCriteriaException("id", "Catalog item ID must be positive"));

		assertThatThrownBy(() -> this.tool.getCatalogItem(0L))
			.isInstanceOf(InvalidCatalogCriteriaException.class)
			.hasMessage("Catalog item ID must be positive");
	}

	@Test
	void toolNameIsStableAndExactlyGetCatalogItem() {
		assertThat(definition().name()).isEqualTo("get_catalog_item");
	}

	@Test
	void descriptionIsModelReadableAndStatesTheContract() {
		String description = definition().description();

		assertThat(description).isNotBlank();
		assertThat(description).contains("catalog item", "identifier", "search_catalog", "error", "createdAt",
				"updatedAt");
	}

	@Test
	void inputSchemaDeclaresOneRequiredIntegerIdentifier() {
		Map<String, Object> schema = schema();

		assertThat(schema).containsEntry("$schema", "https://json-schema.org/draft/2020-12/schema")
			.containsEntry("type", "object")
			.containsEntry("additionalProperties", false);
		assertThat(schema).containsEntry("required", List.of("id"));
		assertThat(properties()).containsOnlyKeys("id");
		assertThat(property("id")).containsEntry("type", "integer");
		assertThat((String) property("id").get("description")).isNotBlank();
	}

	@Test
	void inputSchemaLeavesThePositiveIdentifierRuleToTheCatalogService() {
		assertThat(property("id")).doesNotContainKeys("enum", "minimum", "maximum", "exclusiveMinimum",
				"exclusiveMaximum");
	}

	@Test
	void adapterDependsOnlyOnTheCatalogService() {
		assertThat(GetCatalogItemTool.class.getDeclaredConstructors()).hasSize(1);
		assertThat(GetCatalogItemTool.class.getDeclaredConstructors()[0].getParameterTypes())
			.containsExactly(CatalogService.class);
		assertThat(GetCatalogItemTool.class.getDeclaredFields())
			.allSatisfy(field -> assertThat(field.getType()).isEqualTo(CatalogService.class));
	}

	@Test
	void adapterTypeNeverReferencesThePersistenceLayer() {
		List<Class<?>> adapterTypes = List.of(GetCatalogItemTool.class);

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

	private static CatalogItemView seededItem() {
		return new CatalogItemView(16L, "SVC-104", "Network Health Assessment", "SERVICE",
				"Review office network configuration and provide a prioritized findings report.",
				new BigDecimal("199.00"), true, OffsetDateTime.parse("2026-01-15T09:00:00Z"),
				OffsetDateTime.parse("2026-01-15T09:00:00Z"));
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

}
