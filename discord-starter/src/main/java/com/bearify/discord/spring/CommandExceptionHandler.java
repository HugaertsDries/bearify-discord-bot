package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.CommandInteraction;

import java.lang.reflect.Method;

/**
 * Holds a reference to a bean + method that handles a specific exception type.
 */
class CommandExceptionHandler {

    private final Object bean;
    private final Method method;

    CommandExceptionHandler(Object bean, Method method) {
        this.bean = bean;
        this.method = method;
        this.method.setAccessible(true);
    }

    void invoke(CommandInteraction interaction, Throwable exception) {
        try {
            method.invoke(bean, interaction, exception);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke exception handler: " + method, e);
        }
    }
}
