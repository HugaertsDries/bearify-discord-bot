package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.spring.annotation.HandleException;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps exception types to their handler methods, walking up the exception hierarchy to find
 * the most specific match.
 */
public class CommandExceptionHandlerRegistry {

    private final ApplicationContext context;

    private final Map<Class<? extends Throwable>, CommandExceptionHandler> handlers = new HashMap<>();

    public CommandExceptionHandlerRegistry(ApplicationContext context) {
        this.context = context;
    }

    void register(String name, HandleException annotation, Method method) {
        if (context == null) {
            throw new IllegalStateException("Lazy exception handler registration requires an ApplicationContext");
        }
        handlers.put(annotation.value(), new CommandExceptionHandler(context, name, method));
    }

    public void handle(CommandInteraction interaction, Throwable exception) {
        CommandExceptionHandler handler = findHandler(exception.getClass());
        if (handler != null) {
            handler.handle(interaction, exception);
            return;
        }
        LoggerFactory.getLogger(CommandExceptionHandlerRegistry.class).warn("Unhandled command exception", exception);
        interaction.reply("Something went wrong. Please try again later.").ephemeral().send();
    }

    private CommandExceptionHandler findHandler(Class<?> exceptionType) {
        Class<?> type = exceptionType;
        while (type != null && type != Object.class) {
            CommandExceptionHandler handler = handlers.get(type);
            if (handler != null) {
                return handler;
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
