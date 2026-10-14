package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.ButtonInteraction;
import com.bearify.discord.api.interaction.InteractionType;
import com.bearify.discord.spring.annotation.Interaction;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ButtonRegistry {

    private final Map<String, Consumer<ButtonInteraction>> handlers = new HashMap<>();
    private final ApplicationContext context;

    ButtonRegistry(ApplicationContext context) {
        this.context = context;
    }

    void register(String beanName, Interaction interaction, Method method) {
        if (interaction.type() != InteractionType.BUTTON) {
            return;
        }
        method.setAccessible(true);
        if (handlers.putIfAbsent(interaction.value(), received -> HandlerInvoker.invoke(context, beanName, method, received)) != null) {
            throw new IllegalStateException("Duplicate button interaction '" + interaction.value() + "'");
        }
    }

    public void handle(ButtonInteraction interaction) {
        Consumer<ButtonInteraction> handler = handlers.get(interaction.getCustomId());
        if (handler == null) {
            interaction.reply("This button is not supported.").ephemeral().send();
            return;
        }
        handler.accept(interaction);
    }
}
