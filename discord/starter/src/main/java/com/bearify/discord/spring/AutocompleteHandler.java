package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.AutocompleteInteraction;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class AutocompleteHandler {

    private final ApplicationContext context;
    private final String name;
    private final Method method;

    AutocompleteHandler(ApplicationContext context, String name, Method method) {
        this.context = context;
        this.name = name;
        this.method = method;
        this.method.setAccessible(true);
    }

    void invoke(AutocompleteInteraction interaction) {
        Object target = context.getBean(name);
        Method invocable = AopUtils.selectInvocableMethod(method, target.getClass());
        try {
            invocable.invoke(target, interaction);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Autocomplete handler threw a checked exception: " + invocable, cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not access autocomplete handler: " + invocable, e);
        }
    }
}
