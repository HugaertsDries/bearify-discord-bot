package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.InteractionType;
import com.bearify.discord.api.interaction.MessageContextInteraction;
import com.bearify.discord.api.model.CommandDefinition;
import com.bearify.discord.spring.annotation.Interaction;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Maps message context-menu command names to their handler methods. Context-menu commands are top-level in Discord,
 * so an {@link com.bearify.discord.spring.annotation.InteractionGroup} on the controller doesn't apply to them.
 */
public class MessageContextRegistry {

    private final Map<String, Consumer<MessageContextInteraction>> handlers = new HashMap<>();
    private final ApplicationContext context;

    MessageContextRegistry(ApplicationContext context) {
        this.context = context;
    }

    void register(String beanName, Interaction interaction, Method method) {
        if (interaction.type() != InteractionType.MESSAGE_CONTEXT) {
            return;
        }
        method.setAccessible(true);
        if (handlers.putIfAbsent(interaction.value(), received -> HandlerInvoker.invoke(context, beanName, method, received)) != null) {
            throw new IllegalStateException("Duplicate message context interaction '" + interaction.value() + "'");
        }
    }

    public List<CommandDefinition> getDefinitions() {
        return handlers.keySet().stream().map(CommandDefinition::message).toList();
    }

    public void handle(MessageContextInteraction interaction) {
        Consumer<MessageContextInteraction> handler = handlers.get(interaction.getName());
        if (handler == null) {
            interaction.reply("This command is not supported.").ephemeral().send();
            return;
        }
        handler.accept(interaction);
    }
}
