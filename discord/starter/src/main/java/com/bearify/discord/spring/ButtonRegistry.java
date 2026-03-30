package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.ButtonInteraction;
import com.bearify.discord.api.interaction.InteractionType;
import com.bearify.discord.spring.annotation.DiscordController;
import com.bearify.discord.spring.annotation.Interaction;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ButtonRegistry {

    private final Map<String, ButtonHandler> handlers = new HashMap<>();
    private final ApplicationContext context;

    ButtonRegistry(ApplicationContext context) {
        this.context = context;
    }

    void register(String beanName, Interaction interaction, Method method) {
        if (interaction.type() != InteractionType.BUTTON) {
            return;
        }
        DiscordController controller = AnnotationUtils.findAnnotation(method.getDeclaringClass(), DiscordController.class);
        if (controller == null) {
            throw new IllegalStateException("Interaction " + interaction + " has no @DiscordController annotation");
        }
        if (handlers.putIfAbsent(interaction.value(), new ButtonHandler(context, beanName, method)) != null) {
            throw new IllegalStateException("Duplicate button interaction '" + interaction.value() + "'");
        }
    }

    public void handle(ButtonInteraction interaction) {
        ButtonHandler handler = handlers.get(interaction.getCustomId());
        if (handler == null) {
            interaction.reply("This button is not supported.").ephemeral().send();
            return;
        }
        handler.invoke(interaction);
    }
}
