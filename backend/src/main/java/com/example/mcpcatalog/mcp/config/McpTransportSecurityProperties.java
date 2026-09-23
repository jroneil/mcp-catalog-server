package com.example.mcpcatalog.mcp.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Request-origin protection settings for the local Streamable HTTP MCP transport.
 *
 * <p>Entries use the MCP Java SDK's literal-match syntax: an exact value such as
 * {@code http://127.0.0.1:8080} or a port wildcard such as {@code http://127.0.0.1:*}.
 * An empty {@code allowedOrigins} list rejects any request that sends a non-blank
 * {@code Origin} header. An empty {@code allowedHosts} list disables {@code Host}
 * validation.
 */
@ConfigurationProperties(prefix = "mcp.server.security")
public class McpTransportSecurityProperties {

	private List<String> allowedOrigins = List.of();

	private List<String> allowedHosts = List.of();

	public List<String> getAllowedOrigins() {
		return this.allowedOrigins;
	}

	public void setAllowedOrigins(List<String> allowedOrigins) {
		this.allowedOrigins = requireList(allowedOrigins, "allowedOrigins");
	}

	public List<String> getAllowedHosts() {
		return this.allowedHosts;
	}

	public void setAllowedHosts(List<String> allowedHosts) {
		this.allowedHosts = requireList(allowedHosts, "allowedHosts");
	}

	private static List<String> requireList(List<String> values, String name) {
		if (values == null) {
			throw new IllegalArgumentException("mcp.server.security." + name + " must not be null");
		}
		if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
			throw new IllegalArgumentException("mcp.server.security." + name + " must not contain blank entries");
		}
		return List.copyOf(values);
	}

}
