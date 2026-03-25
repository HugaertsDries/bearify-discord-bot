package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.spring.annotation.HandleException;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
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
        Class<?> type = exception.getClass();
        while (type != null && type != Object.class) {
            CommandExceptionHandler handler = handlers.get(type);
            if (handler != null) {
                handler.handle(interaction, exception);
                return;
            }
            type = type.getSuperclass();
        }
        interaction.reply("Something went wrong. Please try again later.").ephemeral().send();
    }
}
