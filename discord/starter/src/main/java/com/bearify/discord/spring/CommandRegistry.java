package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.model.CommandDefinition;
import com.bearify.discord.spring.annotation.Interaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Maps interaction names to their handler methods and builds the list of
 * {@link CommandDefinition}s that get registered with Discord.
 */
public class CommandRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(CommandRegistry.class);

    private final Map<String, CommandHandler> handlers = new HashMap<>();
    private final List<CommandDefinition> definitions = new ArrayList<>();
    private final CommandExceptionHandlerRegistry exceptionHandlerRegistry;

    CommandRegistry(CommandExceptionHandlerRegistry exceptionHandlerRegistry) {
        this.exceptionHandlerRegistry = exceptionHandlerRegistry;
    }

    void register(Interaction annotation, Object bean, Method method) {
        String name = annotation.value();

        if (handlers.containsKey(name)) {
            throw new IllegalStateException(
                    "Duplicate interaction name '" + name + "' — already registered by: "
                    + handlers.get(name));
        }

        handlers.put(name, new CommandHandler(bean, method));
        definitions.add(new CommandDefinition(name, annotation.description()));
        LOG.info("Registered command '{}'", name);
    }

    public List<CommandDefinition> getDefinitions() {
        return Collections.unmodifiableList(definitions);
    }

    public void dispatch(CommandInteraction interaction) {
        CommandHandler handler = handlers.get(interaction.getName());
        if (handler == null) {
            interaction.reply("This command is not supported.").ephemeral().send();
            return;
        }
        try {
            handler.invoke(interaction);
        } catch (RuntimeException e) {
            exceptionHandlerRegistry.handle(interaction, e);
        }
    }
}
