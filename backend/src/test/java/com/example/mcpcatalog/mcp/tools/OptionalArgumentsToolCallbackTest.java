package com.example.mcpcatalog.mcp.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OptionalArgumentsToolCallbackTest {

	private final ToolCallback delegate = mock(ToolCallback.class);

	private final OptionalArgumentsToolCallback callback = new OptionalArgumentsToolCallback(this.delegate);

	@Test
	void absentArgumentsBecomeAnEmptyArgumentObject() {
		when(this.delegate.call("{}")).thenReturn("ok");

		assertThat(this.callback.call(null)).isEqualTo("ok");
		assertThat(this.callback.call("")).isEqualTo("ok");
		assertThat(this.callback.call("   ")).isEqualTo("ok");
		assertThat(this.callback.call("null")).isEqualTo("ok");
	}

	@Test
	void providedArgumentsAreForwardedUnchanged() {
		when(this.delegate.call("{\"pageSize\":5}")).thenReturn("ok");
		when(this.delegate.call("{\"pageSize\":5}", null)).thenReturn("ok");

		assertThat(this.callback.call("{\"pageSize\":5}")).isEqualTo("ok");
		assertThat(this.callback.call("{\"pageSize\":5}", null)).isEqualTo("ok");
	}

	@Test
	void toolDefinitionIsDelegatedUnchanged() {
		ToolDefinition definition = mock(ToolDefinition.class);
		when(this.delegate.getToolDefinition()).thenReturn(definition);

		assertThat(this.callback.getToolDefinition()).isSameAs(definition);
		verify(this.delegate).getToolDefinition();
	}

}
