package com.bearify.discord.spring;

import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.DiscordClientFactory;
import com.bearify.discord.spring.annotation.Command;
import com.bearify.discord.spring.annotation.CommandAdvice;
import com.bearify.discord.spring.annotation.HandleException;
import com.bearify.discord.spring.annotation.Interaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnBean(DiscordClientFactory.class)
@EnableConfigurationProperties(DiscordProperties.class)
public class DiscordAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(DiscordAutoConfiguration.class);

    private final AnnotationScanner scanner = new AnnotationScanner();

    @Bean
    public CommandExceptionHandlerRegistry commandExceptionHandlerRegistry(ApplicationContext context) {
        CommandExceptionHandlerRegistry registry = new CommandExceptionHandlerRegistry(context);
        scanner.scan(context, CommandAdvice.class, HandleException.class, registry::register);
        return registry;
    }

    @Bean
    public CommandRegistry commandRegistry(ApplicationContext context, CommandExceptionHandlerRegistry exceptionHandlerRegistry) {
        CommandRegistry registry = new CommandRegistry(context, exceptionHandlerRegistry);
        long start = System.currentTimeMillis();
        scanner.scan(context, Command.class, Interaction.class, registry::register);
        LOG.info("Finished scanning for commands: {} registered in {} ms", registry.getDefinitions().size(), System.currentTimeMillis() - start);
        return registry;
    }

    @Bean
    public DiscordClient discordClient(DiscordClientFactory factory, CommandRegistry registry) {
        return factory.create(registry.getDefinitions(), registry::handle);
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
