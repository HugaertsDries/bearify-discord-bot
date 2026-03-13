package com.bearify.discord.spring;

import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.DiscordClientFactory;
import com.bearify.discord.spring.annotation.Command;
import com.bearify.discord.spring.annotation.DiscordControllerAdvice;
import com.bearify.discord.spring.annotation.HandleException;
import com.bearify.discord.spring.annotation.Interaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.Map;

@AutoConfiguration
@ConditionalOnBean(DiscordClientFactory.class)
@EnableConfigurationProperties(DiscordProperties.class)
public class DiscordAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DiscordAutoConfiguration.class);

    @Bean
    public CommandExceptionHandlerRegistry commandExceptionHandlerRegistry(ApplicationContext context) {
        CommandExceptionHandlerRegistry registry = new CommandExceptionHandlerRegistry();

        context.getBeansWithAnnotation(DiscordControllerAdvice.class).values().forEach(bean -> {
            Class<?> targetClass = AopUtils.getTargetClass(bean);

            Map<Method, HandleException> annotatedMethods = MethodIntrospector.selectMethods(
                    targetClass,
                    (MethodIntrospector.MetadataLookup<HandleException>) method ->
                            AnnotationUtils.findAnnotation(method, HandleException.class));

            annotatedMethods.forEach((method, annotation) ->
                    registry.register(annotation, bean, method));
        });

        return registry;
    }

    @Bean
    public CommandRegistry commandRegistry(ApplicationContext context, CommandExceptionHandlerRegistry exceptionHandlerRegistry) {
        CommandRegistry registry = new CommandRegistry(exceptionHandlerRegistry);

        long start = System.currentTimeMillis();

        context.getBeansWithAnnotation(Command.class).values().forEach(bean -> {
            Class<?> targetClass = AopUtils.getTargetClass(bean);

            Map<Method, Interaction> annotatedMethods = MethodIntrospector.selectMethods(
                    targetClass,
                    (MethodIntrospector.MetadataLookup<Interaction>) method ->
                            AnnotationUtils.findAnnotation(method, Interaction.class));

            annotatedMethods.forEach((method, annotation) ->
                    registry.register(annotation, bean, method));
        });

        log.info("Finished scanning for commands: {} registered in {} ms",
                registry.getDefinitions().size(), System.currentTimeMillis() - start);

        return registry;
    }

    @Bean
    public DiscordClient discordClient(DiscordClientFactory factory, CommandRegistry registry) {
        return factory.create(registry.getDefinitions(), registry::dispatch);
    }

    @Bean
    public SmartLifecycle discordLifecycle(DiscordClient discordClient, DiscordProperties properties) {
        return new SmartLifecycle() {
            private volatile boolean running = false;

            @Override
            public void start() {
                properties.guildId().ifPresentOrElse(
                        guildId -> discordClient.start(properties.token(), guildId),
                        () -> discordClient.start(properties.token())
                );
                running = true;
            }

            @Override
            public void stop() { discordClient.shutdown(); running = false; }

            @Override
            public boolean isRunning() { return running; }

            @Override
            public int getPhase() { return Integer.MAX_VALUE - 100; }
        };
    }
}
