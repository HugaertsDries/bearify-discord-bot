package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.CommandInteraction;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Holds a reference to a bean + method that handles a specific exception type.
 */
class CommandExceptionHandler {

    private final ApplicationContext context;

    private final String name;
    private final Method method;

    CommandExceptionHandler(ApplicationContext context, String name, Method method) {
        this.context = context;
        this.name = name;
        this.method = method;
        this.method.setAccessible(true);
    }

    void handle(CommandInteraction interaction, Throwable exception) {
        Object target = context.getBean(name);
        Method invocable = AopUtils.selectInvocableMethod(method, target.getClass());
        try {
            invocable.invoke(target, interaction, exception);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Exception handler threw a checked exception: " + invocable, cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke exception handler: " + invocable, e);
        }
    }
}
