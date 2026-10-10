package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.ButtonInteraction;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;

class ButtonHandler {

    private final ApplicationContext context;
    private final String name;
    private final Method method;

    ButtonHandler(ApplicationContext context, String name, Method method) {
        this.context = context;
        this.name = name;
        this.method = method;
        this.method.setAccessible(true);
    }

    void invoke(ButtonInteraction interaction) {
        HandlerInvoker.invoke(context, name, method, interaction);
    }
}
