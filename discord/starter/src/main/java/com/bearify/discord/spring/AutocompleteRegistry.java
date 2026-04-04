package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.AutocompleteInteraction;
import com.bearify.discord.api.interaction.InteractionType;
import com.bearify.discord.spring.annotation.DiscordController;
import com.bearify.discord.spring.annotation.Interaction;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutocompleteRegistry {

    private final Map<String, AutocompleteHandler> handlers = new HashMap<>();
    private final ApplicationContext context;

    AutocompleteRegistry(ApplicationContext context) {
        this.context = context;
    }

    void register(String beanName, Interaction interaction, Method method) {
        if (interaction.type() != InteractionType.AUTOCOMPLETE) {
            return;
        }
        DiscordController controller = AnnotationUtils.findAnnotation(method.getDeclaringClass(), DiscordController.class);
        if (controller == null) {
            throw new IllegalStateException("Interaction " + interaction + " has no @DiscordController annotation");
        }
        if (handlers.putIfAbsent(interaction.value(), new AutocompleteHandler(context, beanName, method)) != null) {
            throw new IllegalStateException("Duplicate autocomplete interaction '" + interaction.value() + "'");
        }
    }

    public void handle(AutocompleteInteraction interaction) {
        AutocompleteHandler handler = handlers.get(interaction.getId());
        if (handler == null) {
            interaction.reply(List.of());
            return;
        }
        handler.invoke(interaction);
    }
}
