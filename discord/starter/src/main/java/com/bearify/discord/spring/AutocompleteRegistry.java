package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.AutocompleteInteraction;
import com.bearify.discord.api.interaction.InteractionType;
import com.bearify.discord.spring.annotation.Interaction;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class AutocompleteRegistry {

    private final Map<String, Consumer<AutocompleteInteraction>> handlers = new HashMap<>();
    private final ApplicationContext context;

    AutocompleteRegistry(ApplicationContext context) {
        this.context = context;
    }

    void register(String beanName, Interaction interaction, Method method) {
        if (interaction.type() != InteractionType.AUTOCOMPLETE) {
            return;
        }
        method.setAccessible(true);
        if (handlers.putIfAbsent(interaction.value(), received -> HandlerInvoker.invoke(context, beanName, method, received)) != null) {
            throw new IllegalStateException("Duplicate autocomplete interaction '" + interaction.value() + "'");
        }
    }

    public void handle(AutocompleteInteraction interaction) {
        Consumer<AutocompleteInteraction> handler = handlers.get(interaction.getId());
        if (handler == null) {
            interaction.reply(List.of());
            return;
        }
        handler.accept(interaction);
    }
}
