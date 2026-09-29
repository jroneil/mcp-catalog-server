package com.example.mcpcatalog.ai.config;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Offline profile/credential checks. No API key or provider request is created. */
class DeepSeekProfileTest {

    private Properties profile() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-deepseek.yml"));
        return yaml.getObject();
    }

    @Test
    void profileReusesExistingHostedPathAndBindsOnlyExternalCredential() {
        Properties profile = profile();
        assertThat(profile.getProperty("catalog.ai.provider")).isEqualTo("bailian");
        assertThat(profile.getProperty("spring.ai.openai.base-url")).isEqualTo("https://api.deepseek.com");
        assertThat(profile.getProperty("spring.ai.openai.chat.model")).isEqualTo("deepseek-flash");
        assertThat(profile.getProperty("spring.ai.openai.api-key")).isEqualTo("${DEEPSEEK_API_KEY}");
        assertThat(profile.stringPropertyNames()).noneMatch(name -> name.contains("ollama"));
        var environment = new MockEnvironment().withProperty("catalog.ai.provider", "bailian");
        new AiProviderEnvironmentPostProcessor().postProcessEnvironment(environment, null);
        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("openai");
    }

    @Test
    void missingCredentialIsRejectedLocally() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new CatalogAssistantConfiguration.DeepSeekCredentialRequirement(new MockEnvironment()))
            .withMessage("DEEPSEEK_API_KEY must be supplied externally for the deepseek profile");
    }

    @Test
    void credentialGuardDoesNotApplyToDefaultLocalContext() {
        new ApplicationContextRunner()
            .withUserConfiguration(CatalogAssistantConfiguration.DeepSeekCredentialRequirement.class)
            .withInitializer(context -> context.setEnvironment(new MockEnvironment()))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(CatalogAssistantConfiguration.DeepSeekCredentialRequirement.class);
            });
    }

    @Test
    void activeProfileFailsContextStartupBeforeAnyProviderRequestWithoutCredential() {
        new ApplicationContextRunner()
            .withUserConfiguration(CatalogAssistantConfiguration.DeepSeekCredentialRequirement.class)
            .withInitializer(context -> {
                var environment = new MockEnvironment();
                environment.setActiveProfiles("deepseek");
                context.setEnvironment(environment);
            })
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "DEEPSEEK_API_KEY must be supplied externally for the deepseek profile");
            });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void blankCredentialIsRejectedLocally(String blank) {
        var environment = new MockEnvironment().withProperty("DEEPSEEK_API_KEY", blank);
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new CatalogAssistantConfiguration.DeepSeekCredentialRequirement(environment))
            .withMessage("DEEPSEEK_API_KEY must be supplied externally for the deepseek profile");
    }
}
