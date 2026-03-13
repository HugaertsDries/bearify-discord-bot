package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.CommandInteraction;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Holds a reference to a bean + method that handles a specific command.
 */
class CommandHandler {

    private final Object bean;
    private final Method method;

    CommandHandler(Object bean, Method method) {
        this.bean = bean;
        this.method = method;
        this.method.setAccessible(true);
    }

    void invoke(CommandInteraction interaction) {
        try {
            method.invoke(bean, interaction);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Command handler threw a checked exception: " + method, cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Could not access command handler: " + method, e);
        }
    }
}
