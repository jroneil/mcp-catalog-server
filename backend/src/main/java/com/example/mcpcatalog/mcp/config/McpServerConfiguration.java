package com.example.mcpcatalog.mcp.config;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP adapter wiring for the synchronous WebMVC Streamable HTTP transport.
 *
 * <p>Spring AI's {@code McpServerStreamableHttpWebMvcAutoConfiguration} builds the
 * transport provider without a security validator, and the SDK builder therefore
 * defaults to {@link ServerTransportSecurityValidator#NOOP}. Exposing the provider
 * here (the auto-configuration backs off on the same bean type) lets this slice
 * apply the SDK's native Origin/Host validation.
 *
 * <p>This class configures transport only. It contains no catalog business logic
 * and no catalog types, and it never touches repositories.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(McpSchema.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "protocol", havingValue = "STREAMABLE",
		matchIfMissing = true)
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "stdio", havingValue = "false", matchIfMissing = true)
@EnableConfigurationProperties({ McpTransportSecurityProperties.class, McpServerStreamableHttpProperties.class })
public class McpServerConfiguration {

	/**
	 * Native SDK request-origin protection driven by an explicit allowlist.
	 */
	@Bean
	@ConditionalOnMissingBean
	ServerTransportSecurityValidator mcpTransportSecurityValidator(McpTransportSecurityProperties properties) {
		return DefaultServerTransportSecurityValidator.builder()
			.allowedOrigins(properties.getAllowedOrigins())
			.allowedHosts(properties.getAllowedHosts())
			.build();
	}

	/**
	 * Streamable HTTP transport at the configured endpoint, with Origin validation.
	 * Replaces the auto-configured NOOP-validating provider.
	 */
	@Bean
	WebMvcStreamableServerTransportProvider mcpStreamableHttpTransportProvider(
			@Qualifier("mcpServerJsonMapper") JsonMapper mcpServerJsonMapper,
			McpServerStreamableHttpProperties streamableHttpProperties,
			ServerTransportSecurityValidator mcpTransportSecurityValidator) {
		return WebMvcStreamableServerTransportProvider.builder()
			.jsonMapper(new JacksonMcpJsonMapper(mcpServerJsonMapper))
			.mcpEndpoint(streamableHttpProperties.getMcpEndpoint())
			.keepAliveInterval(streamableHttpProperties.getKeepAliveInterval())
			.disallowDelete(streamableHttpProperties.isDisallowDelete())
			.securityValidator(mcpTransportSecurityValidator)
			.build();
	}

}
