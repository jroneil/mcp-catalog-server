package com.example.mcpcatalog.ai;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

/**
 * Per-request record of the single permitted catalog capability invocation (D5).
 *
 * <p>It captures only what the response contract needs: the arguments the model actually
 * used and the capability result those arguments produced. Enforcing the one-call bound
 * here makes it stricter than the framework defaults and independent of any provider.
 */
final class CatalogCapabilityRecorder {

	private int attempts;

	private String argumentsJson;

	private String resultJson;

	/** Bounds and records the tool calls the model requested in this iteration. */
	void recordRequested(List<AssistantMessage.ToolCall> calls) {
		for (AssistantMessage.ToolCall call : calls) {
			this.attempts++;
			if (this.attempts > 1) {
				throw new CatalogAssistantInternalException(
						new IllegalStateException("more than one catalog capability invocation was attempted"));
			}
			this.argumentsJson = call.arguments();
		}
	}

	/** Records the capability result produced for the accepted invocation. */
	void recordResult(List<Message> conversationHistory) {
		if (this.argumentsJson == null || this.resultJson != null) {
			return;
		}
		for (Message message : conversationHistory) {
			if (message instanceof ToolResponseMessage toolResponses) {
				for (ToolResponseMessage.ToolResponse response : toolResponses.getResponses()) {
					this.resultJson = response.responseData();
					return;
				}
			}
		}
	}

	boolean invoked() {
		return this.resultJson != null;
	}

	int attempts() {
		return this.attempts;
	}

	String argumentsJson() {
		return this.argumentsJson;
	}

	String resultJson() {
		return this.resultJson;
	}

}
