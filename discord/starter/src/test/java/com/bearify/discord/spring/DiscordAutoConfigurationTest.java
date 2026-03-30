package com.bearify.discord.spring;

import com.bearify.discord.api.gateway.Activity;
import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.interaction.ButtonInteraction;
import com.bearify.discord.api.interaction.InteractionType;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.model.CommandDefinition;
import com.bearify.discord.spring.annotation.DiscordController;
import com.bearify.discord.spring.annotation.CommandAdvice;
import com.bearify.discord.spring.annotation.HandleException;
import com.bearify.discord.spring.annotation.Interaction;
import com.bearify.discord.spring.annotation.InteractionGroup;
import com.bearify.discord.testing.MockCommandInteraction;
import com.bearify.discord.testing.MockButtonInteraction;
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

    @DiscordController
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

    @DiscordController
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

    @DiscordController
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

    @DiscordController
    static class ButtonController {
        @Interaction(type = InteractionType.BUTTON, value = "player:pause-play")
        void pausePlay(ButtonInteraction interaction) {
            interaction.reply("ok").ephemeral().send();
        }
    }

    @DiscordController
    @InteractionGroup(value = "music", description = "Music commands")
    static class GroupedCommand {
        @Interaction(value = "play", description = "Play a song")
        void play(CommandInteraction interaction) {
            interaction.reply("playing").send();
        }
    }

    @DiscordController
    static class FailingCommand {
        @Interaction(value = "boom", description = "Always throws")
        void boom(CommandInteraction interaction) {
            throw new IllegalStateException("boom");
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
    void registersGroupedCommandsThroughInteractionGroup() {
        contextRunner
                .withPropertyValues("discord.token=test-token")
                .withUserConfiguration(MockClientConfig.class, GroupedCommand.class)
                .run(ctx -> {
                    CommandRegistry registry = ctx.getBean(CommandRegistry.class);
                    assertThat(registry.getDefinitions()).extracting(CommandDefinition::name).contains("music");
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
    void forwardsConfiguredActivityToDiscordClientFactory() {
        contextRunner
                .withPropertyValues(
                        "discord.token=my-token",
                        "discord.activity.type=PLAYING",
                        "discord.activity.text=with slash commands"
                )
                .withUserConfiguration(MockClientConfig.class)
                .run(ctx -> {
                    MockDiscordClient.Factory factory = ctx.getBean(MockDiscordClient.Factory.class);
                    assertThat(factory.getLastCreatedActivity())
                            .contains(new Activity(Activity.Type.PLAYING, "with slash commands"));
                });
    }

    @Test
    void leavesActivityEmptyWhenNotConfigured() {
        contextRunner
                .withPropertyValues("discord.token=my-token")
                .withUserConfiguration(MockClientConfig.class)
                .run(ctx -> {
                    MockDiscordClient.Factory factory = ctx.getBean(MockDiscordClient.Factory.class);
                    assertThat(factory.getLastCreatedActivity()).isEmpty();
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
    void routesDispatchedButtonThroughRegistryToHandler() {
        contextRunner
                .withPropertyValues("discord.token=test-token")
                .withUserConfiguration(MockClientConfig.class, ButtonController.class)
                .run(ctx -> {
                    MockDiscordClient client = ctx.getBean(MockDiscordClient.Factory.class).getLastCreated().orElseThrow();
                    MockButtonInteraction interaction = MockButtonInteraction.forButton("player:pause-play").build();

                    client.dispatchButton(interaction);

                    assertThat(interaction.getReplies()).hasSize(1);
                    assertThat(interaction.getReplies().getFirst().getContent()).isEqualTo("ok");
                    assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
                });
    }

    @Test
    void usesFallbackReplyWhenNoExceptionHandlerMatches() {
        contextRunner
                .withPropertyValues("discord.token=test-token")
                .withUserConfiguration(MockClientConfig.class, FailingCommand.class)
                .run(ctx -> {
                    MockDiscordClient client = ctx.getBean(MockDiscordClient.Factory.class).getLastCreated().orElseThrow();
                    MockCommandInteraction interaction = MockCommandInteraction.forCommand("boom").build();

                    client.dispatch(interaction);

                    assertThat(interaction.getReplies()).singleElement().satisfies(reply -> {
                        assertThat(reply.getContent()).isEqualTo("Something went wrong. Please try again later.");
                        assertThat(reply.isEphemeral()).isTrue();
                    });
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

    @Test
    void failsWhenActivityTextIsBlank() {
        contextRunner
                .withPropertyValues(
                        "discord.token=my-token",
                        "discord.activity.type=PLAYING",
                        "discord.activity.text=   "
                )
                .withUserConfiguration(MockClientConfig.class)
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void failsWhenActivityTypeIsMissing() {
        contextRunner
                .withPropertyValues(
                        "discord.token=my-token",
                        "discord.activity.text=with slash commands"
                )
                .withUserConfiguration(MockClientConfig.class)
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
