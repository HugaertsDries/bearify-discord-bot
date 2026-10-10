package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.AutocompleteInteraction;
import org.springframework.context.ApplicationContext;

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
        HandlerInvoker.invoke(context, name, method, interaction);
    }
}
