package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.spring.annotation.HandleException;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps exception types to their handler methods, walking up the exception hierarchy to find
 * the most specific match.
 */
public class CommandExceptionHandlerRegistry {

    private final Map<Class<? extends Throwable>, CommandExceptionHandler> handlers = new HashMap<>();

    void register(HandleException annotation, Object bean, Method method) {
        handlers.put(annotation.value(), new CommandExceptionHandler(bean, method));
    }

    public void handle(CommandInteraction interaction, Throwable exception) {
        Class<?> type = exception.getClass();
        while (type != null && type != Object.class) {
            CommandExceptionHandler handler = handlers.get(type);
            if (handler != null) {
                handler.invoke(interaction, exception);
                return;
            }
            type = type.getSuperclass();
        }
        interaction.reply("Something went wrong. Please try again later.").ephemeral().send();
    }
}
