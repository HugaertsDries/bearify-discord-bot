package com.bearify.discord.spring;

import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.DiscordClientFactory;
import com.bearify.discord.api.interaction.AutocompleteInteraction;
import com.bearify.discord.api.interaction.ButtonInteraction;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.Interaction;
import com.bearify.discord.spring.annotation.DiscordController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
@ConditionalOnBean(DiscordClientFactory.class)
@EnableConfigurationProperties(DiscordProperties.class)
public class DiscordAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(DiscordAutoConfiguration.class);
    private static final String GENERIC_ERROR = "Something went wrong. Please try again later.";

    private final AnnotationScanner scanner = new AnnotationScanner();

    @Bean
    public CommandRegistry commandRegistry(ApplicationContext context) {
        CommandRegistry registry = new CommandRegistry(context);
        long start = System.currentTimeMillis();
        scanner.scan(context, DiscordController.class, com.bearify.discord.spring.annotation.Interaction.class, registry::register);
        LOG.info("Finished scanning for commands: {} registered in {} ms", registry.getDefinitions().size(), System.currentTimeMillis() - start);
        return registry;
    }

    @Bean
    public ButtonRegistry buttonRegistry(ApplicationContext context) {
        ButtonRegistry registry = new ButtonRegistry(context);
        scanner.scan(context, DiscordController.class, com.bearify.discord.spring.annotation.Interaction.class, registry::register);
        return registry;
    }

    @Bean
    public AutocompleteRegistry autocompleteRegistry(ApplicationContext context) {
        AutocompleteRegistry registry = new AutocompleteRegistry(context);
        scanner.scan(context, DiscordController.class, com.bearify.discord.spring.annotation.Interaction.class, registry::register);
        return registry;
    }

    @Bean
    public DiscordClient discordClient(DiscordClientFactory factory,
                                       CommandRegistry registry,
                                       ButtonRegistry buttonRegistry,
                                       AutocompleteRegistry autocompleteRegistry,
                                       DiscordProperties properties) {
        java.util.function.Consumer<Interaction> interactionHandler = interaction -> {
            try {
                if (interaction instanceof CommandInteraction commandInteraction) {
                    registry.handle(commandInteraction);
                    return;
                }
                if (interaction instanceof AutocompleteInteraction autocompleteInteraction) {
                    autocompleteRegistry.handle(autocompleteInteraction);
                    return;
                }
                if (interaction instanceof ButtonInteraction buttonInteraction) {
                    buttonRegistry.handle(buttonInteraction);
                    return;
                }
                throw new IllegalStateException("Unsupported interaction type: " + interaction.getClass().getName());
            } catch (RuntimeException e) {
                LOG.warn("Unhandled interaction exception", e);
                replyWithGenericError(interaction);
            }
        };
        return factory.create(registry.getDefinitions(), interactionHandler, properties.activity());
    }

    private static void replyWithGenericError(Interaction interaction) {
        try {
            switch (interaction) {
                case CommandInteraction command -> command.reply(GENERIC_ERROR).ephemeral().send();
                case ButtonInteraction button -> button.reply(GENERIC_ERROR).ephemeral().send();
                case AutocompleteInteraction autocomplete -> autocomplete.reply(List.of());
                default -> { }
            }
        } catch (RuntimeException e) {
            // e.g. the handler already acknowledged the interaction before throwing
            LOG.warn("Could not reply to interaction after unhandled exception", e);
        }
    }

    @Bean
    public SmartLifecycle discordLifecycle(DiscordClient client, DiscordProperties properties) {
        return new SmartLifecycle() {
            private volatile boolean running = false;

            @Override
            public void start() {
                properties.guildId().ifPresentOrElse(guildId -> client.start(properties.token(), guildId), () -> client.start(properties.token()));
                running = true;
            }

            @Override
            public void stop() { client.shutdown(); running = false; }

            @Override
            public boolean isRunning() { return running; }

            @Override
            public int getPhase() { return Integer.MAX_VALUE - 100; }
        };
    }
}
