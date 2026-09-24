package com.example.mcpcatalog.ai;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Delegates real tool execution to Spring AI's default manager while recording the
 * catalog capability arguments and result for one request (D4/D5).
 *
 * <p>Provider and tool orchestration stays here, outside catalog business logic: the
 * delegate ultimately executes the existing capability adapter, so model-generated
 * arguments still pass through {@code CatalogService} validation.
 */
final class RecordingToolCallingManager implements ToolCallingManager {

	private final ToolCallingManager delegate;

	private final CatalogCapabilityRecorder recorder;

	RecordingToolCallingManager(ToolCallingManager delegate, CatalogCapabilityRecorder recorder) {
		this.delegate = delegate;
		this.recorder = recorder;
	}

	@Override
	public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions toolCallingChatOptions) {
		return this.delegate.resolveToolDefinitions(toolCallingChatOptions);
	}

	@Override
	public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
		AssistantMessage output = chatResponse == null || chatResponse.getResult() == null ? null
				: chatResponse.getResult().getOutput();
		if (output != null) {
			this.recorder.recordRequested(output.getToolCalls());
		}
		ToolExecutionResult result = this.delegate.executeToolCalls(prompt, chatResponse);
		this.recorder.recordResult(result.conversationHistory());
		return result;
	}

}
