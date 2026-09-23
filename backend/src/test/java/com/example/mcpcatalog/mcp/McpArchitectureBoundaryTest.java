package com.example.mcpcatalog.mcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.example.mcpcatalog.mcp.tools.SearchCatalogTool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 7 architecture-boundary verification, extended for Slice 11 REST.
 *
 * <p>These checks read the compiled production classes and search their constant pools,
 * so they catch references in method bodies and signatures alike — a direct repository
 * call, or an MCP type imported by the application layer, cannot hide behind reflection.
 */
class McpArchitectureBoundaryTest {

	private static final String BASE_PACKAGE = "com/example/mcpcatalog/";

	private static final Path MAIN_CLASSES = mainClassesRoot();

	@Test
	void mcpPackageNeverReferencesThePersistenceLayer() {
		List<Path> offenders = classesContaining("mcp", "com/example/mcpcatalog/catalog/persistence");

		assertThat(offenders).as("MCP adapters must call CatalogService, never repositories").isEmpty();
	}

	@Test
	void mcpPackageNeverUsesSqlOrJdbcApis() {
		List<Path> offenders = new java.util.ArrayList<>();
		offenders.addAll(classesContaining("mcp", "java/sql"));
		offenders.addAll(classesContaining("mcp", "javax/sql"));
		offenders.addAll(classesContaining("mcp", "org/springframework/jdbc"));

		assertThat(offenders).as("query mechanics belong to the persistence layer").isEmpty();
	}

	@Test
	void mcpAdaptersDependOnTheApplicationLayerForCatalogAccess() {
		List<Path> toolClasses = classFiles("mcp/tools");

		assertThat(toolClasses).isNotEmpty();
		assertThat(toolClasses.stream().filter(path -> references(path, "com/example/mcpcatalog/catalog/application")))
			.as("the adapters must reach the catalog through the application layer")
			.isNotEmpty();
	}

	@Test
	void catalogApplicationNeverReferencesMcpOrTransportApis() {
		List<Path> offenders = new java.util.ArrayList<>();
		for (String forbidden : List.of("com/example/mcpcatalog/mcp", "io/modelcontextprotocol",
				"org/springframework/ai/mcp", "org/springframework/web", "jakarta/servlet")) {
			offenders.addAll(classesContaining("catalog/application", forbidden));
		}

		assertThat(offenders).as("no MCP, HTTP or servlet type may leak into the service layer").isEmpty();
	}

	@Test
	void catalogPersistenceNeverReferencesMcp() {
		assertThat(classesContaining("catalog/persistence", "com/example/mcpcatalog/mcp")).isEmpty();
	}

	@Test
	void mcpConfigurationContainsNoCatalogBusinessLogic() {
		List<Path> offenders = new java.util.ArrayList<>();
		for (String forbidden : List.of("catalog/application/CatalogService", "catalog/application/CatalogSearchCriteria",
				"catalog/persistence", "java/sql")) {
			offenders.addAll(classesContaining("mcp/config", forbidden));
		}

		assertThat(offenders).as("configuration wires beans only; catalog behavior stays in the adapters/service")
			.isEmpty();
	}

	@Test
	void restControllersAndRequestMappingsExistOnlyInTheRestAdapterPackage() {
		List<Path> mapped = new java.util.ArrayList<>();
		for (String annotation : List.of("RestController", "RequestMapping", "GetMapping",
				"PostMapping", "PutMapping", "PatchMapping", "DeleteMapping", "ControllerAdvice")) {
			mapped.addAll(classesContaining("", "org/springframework/web/bind/annotation/" + annotation));
		}
		mapped.addAll(classesContaining("", "org/springframework/stereotype/Controller"));
		assertThat(mapped).isNotEmpty().allSatisfy(path ->
				assertThat(path).startsWith(MAIN_CLASSES.resolve(BASE_PACKAGE + "rest")));
	}

	@Test
	void restUsesOnlyTheApplicationLayerForCatalogAccess() {
		assertThat(classFiles("rest")).isNotEmpty();
		for (String forbidden : List.of("catalog/persistence", "com/example/mcpcatalog/mcp",
				"io/modelcontextprotocol", "org/springframework/ai", "java/sql", "javax/sql",
				"org/springframework/jdbc", "org/springframework/data")) {
			assertThat(classesContaining("rest", forbidden)).as(forbidden).isEmpty();
		}
		assertThat(classesContaining("rest", "catalog/application/CatalogService")).isNotEmpty();
	}

	@Test
	void catalogLayersNeverDependOnRestOrWebTransport() {
		for (String layer : List.of("catalog/application", "catalog/persistence")) {
			for (String forbidden : List.of("com/example/mcpcatalog/rest", "org/springframework/web",
					"jakarta/servlet")) {
				assertThat(classesContaining(layer, forbidden)).as(layer + " -> " + forbidden).isEmpty();
			}
		}
	}

	@Test
	void noStdioOrLegacySseTransportIsWiredByTheApplication() {
		List<Path> offenders = new java.util.ArrayList<>();
		for (String forbidden : List.of("io/modelcontextprotocol/server/transport/StdioServerTransportProvider",
				"io/modelcontextprotocol/server/transport/SseServerTransportProvider",
				"WebMvcSseServerTransportProvider")) {
			offenders.addAll(classesContaining("", forbidden));
		}

		assertThat(offenders).as("only Streamable HTTP is wired").isEmpty();
	}

	private static List<Path> classesContaining(String relativePackage, String forbiddenInternalName) {
		return classFiles(relativePackage).stream().filter(path -> references(path, forbiddenInternalName)).toList();
	}

	private static boolean references(Path classFile, String forbiddenInternalName) {
		try {
			return new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1)
				.contains(forbiddenInternalName);
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private static List<Path> classFiles(String relativePackage) {
		Path directory = relativePackage.isEmpty() ? MAIN_CLASSES.resolve(BASE_PACKAGE)
				: MAIN_CLASSES.resolve(BASE_PACKAGE + relativePackage);
		if (!Files.isDirectory(directory)) {
			return List.of();
		}
		try (Stream<Path> walk = Files.walk(directory)) {
			return walk.filter(path -> path.toString().endsWith(".class")).toList();
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private static Path mainClassesRoot() {
		try {
			URL location = SearchCatalogTool.class.getProtectionDomain().getCodeSource().getLocation();
			Path root = Path.of(location.toURI());
			assertThat(Files.isDirectory(root)).as("exploded classes directory expected at %s", root).isTrue();
			return root;
		}
		catch (Exception exception) {
			throw new IllegalStateException("cannot locate compiled production classes", exception);
		}
	}

}
