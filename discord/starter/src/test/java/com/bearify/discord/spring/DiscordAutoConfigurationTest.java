package com.bearify.discord.spring;

import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.model.CommandDefinition;
import com.bearify.discord.spring.annotation.Command;
import com.bearify.discord.spring.annotation.CommandAdvice;
import com.bearify.discord.spring.annotation.HandleException;
import com.bearify.discord.spring.annotation.Interaction;
import com.bearify.discord.testing.MockCommandInteraction;
import com.bearify.discord.testing.MockDiscordClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DiscordAutoConfiguration.class));

    @Configuration
    static class MockClientConfig {
        @Bean
        MockDiscordClient.Factory discordClientFactory() {
            return new MockDiscordClient.Factory();
        }
    }

    @Command
    static class HelloCommand {
        @Interaction(value = "hello", description = "Say hello")
        void hello(CommandInteraction interaction) {
            interaction.reply("Hello!").send();
        }
    }

    @CommandAdvice
    static class TestAdvice {
        @HandleException(RuntimeException.class)
        void onError(CommandInteraction interaction, RuntimeException e) {
            interaction.reply("Error: " + e.getMessage()).ephemeral().send();
        }
    }

    @Command
    @Lazy
    static class LazyInitCommand {
        static final AtomicInteger instantiations = new AtomicInteger();

        LazyInitCommand() {
            instantiations.incrementAndGet();
        }

        @Interaction(value = "lazy", description = "Lazy command")
        void lazy(CommandInteraction interaction) {
            interaction.reply("lazy").send();
        }
    }

    @Command
    static class DiscordDependentCommand {
        DiscordDependentCommand(DiscordClient client) {
        }

        @Interaction(value = "dependent", description = "Depends on DiscordClient")
        void dependent(CommandInteraction interaction) {
            interaction.reply("ok").send();
        }
    }

    @CommandAdvice
    static class DiscordDependentAdvice {
        DiscordDependentAdvice(DiscordClient client) {
        }

        @HandleException(RuntimeException.class)
        void onError(CommandInteraction interaction, RuntimeException e) {
            interaction.reply("handled").send();
        }
    }

    // --- HAPPY PATH ---

    @Test
    void registersScannedCommandsInRegistry() {
        contextRunner
                .withPropertyValues("discord.token=test-token")
                .withUserConfiguration(MockClientConfig.class, HelloCommand.class)
                .run(ctx -> {
                    CommandRegistry registry = ctx.getBean(CommandRegistry.class);
                    assertThat(registry.getDefinitions()).hasSize(1);
                    assertThat(registry.getDefinitions().getFirst().name()).isEqualTo("hello");
                });
    }

    @Test
    void registersScannedCommandsWithoutInstantiatingCommandBean() {
        LazyInitCommand.instantiations.set(0);
        contextRunner
                .withPropertyValues("discord.token=test-token")
                .withUserConfiguration(MockClientConfig.class, LazyInitCommand.class)
                .run(ctx -> {
                    CommandRegistry registry = ctx.getBean(CommandRegistry.class);
                    assertThat(registry.getDefinitions()).extracting(CommandDefinition::name).contains("lazy");
                    assertThat(LazyInitCommand.instantiations.get()).isZero();
                });
    }

    @Test
    void createsAndStartsDiscordClientViaFactory() {
        contextRunner
                .withPropertyValues("discord.token=my-token")
                .withUserConfiguration(MockClientConfig.class)
                .run(ctx -> {
                    MockDiscordClient.Factory factory = ctx.getBean(MockDiscordClient.Factory.class);
                    MockDiscordClient created = factory.getLastCreated().orElseThrow();
                    assertThat(ctx.getBean(DiscordClient.class)).isSameAs(created);
                    assertThat(created.isStarted()).isTrue();
                    assertThat(created.getStartedWithToken()).contains("my-token");
                });
    }

    @Test
    void routesDispatchedInteractionThroughRegistryToHandler() {
        contextRunner
                .withPropertyValues("discord.token=test-token")
                .withUserConfiguration(MockClientConfig.class, HelloCommand.class)
                .run(ctx -> {
                    MockDiscordClient client = ctx.getBean(MockDiscordClient.Factory.class).getLastCreated().orElseThrow();
                    MockCommandInteraction interaction = MockCommandInteraction.forCommand("hello").build();
                    client.dispatch(interaction);

                    assertThat(interaction.getReplies()).hasSize(1);
                    assertThat(interaction.getReplies().getFirst().getContent()).isEqualTo("Hello!");
                    assertThat(interaction.getReplies().getFirst().isSent()).isTrue();
                });
    }

    @Test
    void registersCommandAdviceAsExceptionHandler() {
        contextRunner
                .withPropertyValues("discord.token=test-token")
                .withUserConfiguration(MockClientConfig.class, TestAdvice.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(CommandExceptionHandlerRegistry.class));
    }

    @Test
    void startsContextWhenCommandDependsOnDiscordClient() {
        contextRunner
                .withPropertyValues("discord.token=test-token")
                .withUserConfiguration(MockClientConfig.class, DiscordDependentCommand.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(CommandRegistry.class));
    }

    @Test
    void startsContextWhenAdviceDependsOnDiscordClient() {
        contextRunner
                .withPropertyValues("discord.token=test-token")
                .withUserConfiguration(MockClientConfig.class, DiscordDependentAdvice.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(CommandExceptionHandlerRegistry.class));
    }

    @Test
    void dispatchesInteractionAfterLazyStartup() {
        contextRunner
                .withPropertyValues("discord.token=test-token")
                .withUserConfiguration(MockClientConfig.class, DiscordDependentCommand.class)
                .run(ctx -> {
                    MockDiscordClient client = ctx.getBean(MockDiscordClient.Factory.class).getLastCreated().orElseThrow();
                    MockCommandInteraction interaction = MockCommandInteraction.forCommand("dependent").build();

                    client.dispatch(interaction);

                    assertThat(interaction.getReplies()).hasSize(1);
                    assertThat(interaction.getReplies().getFirst().getContent()).isEqualTo("ok");
                });
    }

    // --- EDGE CASES ---

    @Test
    void doesNotActivateWithoutDiscordClientFactory() {
        contextRunner
                .withPropertyValues("discord.token=test-token")
                .withUserConfiguration(HelloCommand.class)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(CommandRegistry.class));
    }

    @Test
    void startsClientWithGuildIdWhenConfigured() {
        contextRunner
                .withPropertyValues("discord.token=my-token", "discord.guild-id=12345")
                .withUserConfiguration(MockClientConfig.class)
                .run(ctx -> {
                    MockDiscordClient client = ctx.getBean(MockDiscordClient.Factory.class).getLastCreated().orElseThrow();
                    assertThat(client.getStartedWithGuildId()).contains("12345");
                });
    }
}
