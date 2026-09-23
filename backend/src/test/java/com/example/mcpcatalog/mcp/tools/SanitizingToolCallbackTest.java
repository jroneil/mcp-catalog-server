package com.example.mcpcatalog.mcp.tools;

import java.sql.SQLException;

import com.example.mcpcatalog.catalog.application.CatalogItemNotFoundException;
import com.example.mcpcatalog.catalog.application.InvalidCatalogCriteriaException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SanitizingToolCallbackTest {

	private static final String SENSITIVE_DETAILS = "simulated failure: "
			+ "SELECT id, sku, name FROM public.catalog_item WHERE name ILIKE ?; "
			+ "jdbc:postgresql://postgres:5432/catalog?user=catalog&password=sup3r-secret; "
			+ "at com.example.mcpcatalog.catalog.persistence.CatalogItemSearchImpl.search(CatalogItemSearchImpl.java:42); "
			+ "Caused by: org.postgresql.util.PSQLException: FATAL: password authentication failed";

	private final ToolCallback delegate = mock(ToolCallback.class);

	private final SanitizingToolCallback callback = new SanitizingToolCallback(this.delegate);

	@Test
	void replacesUnexpectedFailuresWithTheFixedSafeMessage() {
		when(this.delegate.call("{}", null)).thenThrow(new IllegalStateException(SENSITIVE_DETAILS));

		assertThatThrownBy(() -> this.callback.call("{}", null))
			.isInstanceOf(SanitizedToolFailureException.class)
			.hasMessage(SanitizedToolFailureException.MESSAGE);
	}

	@Test
	void neverExposesSqlConnectionStringsCredentialsOrPaths() {
		when(this.delegate.call("{}", null)).thenThrow(new IllegalStateException(SENSITIVE_DETAILS));

		String message = catchMessage(() -> this.callback.call("{}", null));

		assertThat(message).doesNotContain("SELECT", "public.catalog_item", "jdbc:", "postgresql", "user=", "password",
				"sup3r-secret", "Caused by", "Exception", "at com.", ".java:", "/app", "/data");
	}

	@Test
	void sanitizesFailureDetailsCarriedByWrapperExceptions() {
		ToolCallback deepDelegate = mock(ToolCallback.class);
		when(deepDelegate.getToolDefinition()).thenReturn(mock(ToolDefinition.class));
		when(deepDelegate.call("{}", null)).thenThrow(new DataAccessResourceFailureException(
				"Could not get JDBC Connection; nested exception is " + SENSITIVE_DETAILS,
				new SQLException("connection refused for jdbc:postgresql://postgres:5432/catalog")));

		String message = catchMessage(() -> new SanitizingToolCallback(deepDelegate).call("{}", null));

		assertThat(message).isEqualTo(SanitizedToolFailureException.MESSAGE);
		assertThat(message).doesNotContain("JDBC", "jdbc:", "postgres", "SQLException", "Connection");
	}

	@Test
	void retainsTheOriginalFailureAsTheCauseForServerSideDiagnostics() {
		IllegalStateException original = new IllegalStateException(SENSITIVE_DETAILS);
		when(this.delegate.call("{}", null)).thenThrow(original);

		SanitizedToolFailureException thrown = (SanitizedToolFailureException) catchThrowable(
				() -> this.callback.call("{}", null));

		assertThat(thrown.getCause()).isSameAs(original);
		assertThat(thrown.getMessage()).doesNotContain(SENSITIVE_DETAILS);
	}

	@Test
	void sanitizesEvenWhenTheDelegateCannotSupplyItsDefinition() {
		when(this.delegate.call("{}", null)).thenThrow(new IllegalStateException(SENSITIVE_DETAILS));
		when(this.delegate.getToolDefinition()).thenThrow(new IllegalStateException("definition unavailable"));

		assertThatThrownBy(() -> this.callback.call("{}", null))
			.isInstanceOf(SanitizedToolFailureException.class)
			.hasMessage(SanitizedToolFailureException.MESSAGE);
	}

	@Test
	void preservesTheIntendedInvalidCriteriaMessage() {
		when(this.delegate.call("{}", null))
			.thenThrow(new InvalidCatalogCriteriaException("pageSize", "Page size must be between 1 and 100"));

		assertThatThrownBy(() -> this.callback.call("{}", null))
			.isInstanceOf(InvalidCatalogCriteriaException.class)
			.hasMessage("Page size must be between 1 and 100");
	}

	@Test
	void preservesTheIntendedNotFoundMessage() {
		when(this.delegate.call("{}", null)).thenThrow(new CatalogItemNotFoundException(99999L));

		assertThatThrownBy(() -> this.callback.call("{}", null))
			.isInstanceOf(CatalogItemNotFoundException.class)
			.hasMessage("Catalog item not found: 99999");
	}

	@Test
	void preservesIntendedMessagesWrappedByFrameworkExceptions() {
		when(this.delegate.call("{}", null))
			.thenThrow(new RuntimeException("wrapper", new InvalidCatalogCriteriaException("type",
					"Type must be PRODUCT or SERVICE")));

		assertThatThrownBy(() -> this.callback.call("{}", null)).hasMessage("wrapper");
	}

	@Test
	void forwardsSuccessfulResultsAndInputUnchanged() {
		when(this.delegate.call("{\"id\":16}", null)).thenReturn("{\"id\":16}");

		assertThat(this.callback.call("{\"id\":16}", null)).isEqualTo("{\"id\":16}");
		verify(this.delegate).call("{\"id\":16}", null);
	}

	@Test
	void toolDefinitionIsDelegatedUnchanged() {
		ToolDefinition definition = mock(ToolDefinition.class);
		when(this.delegate.getToolDefinition()).thenReturn(definition);

		assertThat(this.callback.getToolDefinition()).isSameAs(definition);
	}

	private static String catchMessage(ThrowingCallable callable) {
		return catchThrowable(callable).getMessage();
	}

	private static Throwable catchThrowable(ThrowingCallable callable) {
		try {
			callable.call();
		}
		catch (Throwable throwable) {
			return throwable;
		}
		throw new AssertionError("expected the callback to fail");
	}

	@FunctionalInterface
	private interface ThrowingCallable {

		void call();

	}

}
