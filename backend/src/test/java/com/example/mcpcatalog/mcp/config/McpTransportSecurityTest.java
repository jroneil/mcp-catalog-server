package com.example.mcpcatalog.mcp.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Documents and verifies the request-origin protection that
 * {@link McpServerConfiguration} applies to the Streamable HTTP MCP transport.
 *
 * <p>These cases exercise the exact validator produced by the adapter configuration,
 * so the documented allowlist behaviour cannot drift from the wired behaviour.
 */
class McpTransportSecurityTest {

	private static final List<String> LOOPBACK_ORIGINS = List.of("http://127.0.0.1:*", "http://localhost:*");

	private static final List<String> LOOPBACK_HOSTS = List.of("127.0.0.1:*", "localhost:*");

	@Test
	void nonBrowserRequestWithoutOriginHeaderIsAllowed() {
		var validator = validator(LOOPBACK_ORIGINS, LOOPBACK_HOSTS);

		assertThatCode(() -> validator.validateHeaders(headers("127.0.0.1:8080", null))).doesNotThrowAnyException();
	}

	@Test
	void loopbackOriginOnAnyPortIsAllowedByPortWildcard() {
		var validator = validator(LOOPBACK_ORIGINS, LOOPBACK_HOSTS);

		assertThatCode(() -> validator.validateHeaders(headers("127.0.0.1:8080", "http://127.0.0.1:41234")))
			.doesNotThrowAnyException();
		assertThatCode(() -> validator.validateHeaders(headers("localhost:8080", "http://localhost:8080")))
			.doesNotThrowAnyException();
	}

	@Test
	void blankOriginValueIsTreatedAsAbsent() {
		var validator = validator(LOOPBACK_ORIGINS, LOOPBACK_HOSTS);

		assertThatCode(() -> validator.validateHeaders(headers("127.0.0.1:8080", ""))).doesNotThrowAnyException();
	}

	@Test
	void crossOriginRequestIsRejectedWith403() {
		var validator = validator(LOOPBACK_ORIGINS, LOOPBACK_HOSTS);

		assertRejectedWith(() -> validator.validateHeaders(headers("127.0.0.1:8080", "http://evil.example")), 403,
				"Invalid Origin header");
	}

	@Test
	void emptyOriginAllowlistRejectsEveryPresentOrigin() {
		var validator = validator(List.of(), LOOPBACK_HOSTS);

		assertRejectedWith(() -> validator.validateHeaders(headers("127.0.0.1:8080", "http://127.0.0.1:8080")), 403,
				"Invalid Origin header");
	}

	@Test
	void nonLoopbackHostHeaderIsRejectedWith421() {
		var validator = validator(LOOPBACK_ORIGINS, LOOPBACK_HOSTS);

		assertRejectedWith(() -> validator.validateHeaders(headers("evil.example:8080", null)), 421,
				"Invalid Host header");
	}

	@Test
	void missingHostHeaderIsRejectedWhenAllowlistConfigured() {
		var validator = validator(LOOPBACK_ORIGINS, LOOPBACK_HOSTS);

		assertRejectedWith(() -> validator.validateHeaders(headers(null, null)), 421, "Invalid Host header");
	}

	@Test
	void hostValidationIsDisabledByAnEmptyAllowlist() {
		var validator = validator(LOOPBACK_ORIGINS, List.of());

		assertThatCode(() -> validator.validateHeaders(headers("evil.example:8080", null))).doesNotThrowAnyException();
	}

	@Test
	void blankAllowlistEntriesAreRejectedAtConfigurationTime() {
		var properties = new McpTransportSecurityProperties();

		assertThatThrownBy(() -> properties.setAllowedOrigins(List.of("http://127.0.0.1:*", " ")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("allowedOrigins");
		assertThatThrownBy(() -> properties.setAllowedHosts(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("allowedHosts");
	}

	private static void assertRejectedWith(ThrowingCallable callable, int expectedStatus, String expectedMessage) {
		ServerTransportSecurityException thrown = catchThrowableOfType(callable,
				ServerTransportSecurityException.class);
		assertThat(thrown).isNotNull();
		assertThat(thrown.getStatusCode()).isEqualTo(expectedStatus);
		assertThat(thrown).hasMessage(expectedMessage);
	}

	private static ServerTransportSecurityValidator validator(List<String> origins, List<String> hosts) {
		var properties = new McpTransportSecurityProperties();
		properties.setAllowedOrigins(origins);
		properties.setAllowedHosts(hosts);
		return new McpServerConfiguration().mcpTransportSecurityValidator(properties);
	}

	private static Map<String, List<String>> headers(String host, String origin) {
		Map<String, List<String>> headers = new LinkedHashMap<>();
		if (host != null) {
			headers.put("Host", List.of(host));
		}
		if (origin != null) {
			headers.put("Origin", List.of(origin));
		}
		return headers;
	}

}
