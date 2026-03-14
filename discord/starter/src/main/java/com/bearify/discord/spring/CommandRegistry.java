package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.model.CommandDefinition;
import com.bearify.discord.api.model.OptionDefinition;
import com.bearify.discord.api.model.OptionDefinition.OptionType;
import com.bearify.discord.spring.annotation.Interaction;
import com.bearify.discord.spring.annotation.Option;
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

    private static final Map<Class<?>, OptionType> OPTION_TYPES = new HashMap<>();

    static {
        OPTION_TYPES.put(String.class, OptionType.STRING);
        OPTION_TYPES.put(int.class, OptionType.INTEGER);
        OPTION_TYPES.put(Integer.class, OptionType.INTEGER);
        OPTION_TYPES.put(long.class, OptionType.INTEGER);
        OPTION_TYPES.put(Long.class, OptionType.INTEGER);
        OPTION_TYPES.put(boolean.class, OptionType.BOOLEAN);
        OPTION_TYPES.put(Boolean.class, OptionType.BOOLEAN);
    }

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
        definitions.add(new CommandDefinition(name, annotation.description(), scanOptions(method)));
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

    private List<OptionDefinition> scanOptions(Method method) {
        return Arrays.stream(method.getParameters())
                .filter(param -> param.isAnnotationPresent(Option.class))
                .map(param -> {
                    Option option = param.getAnnotation(Option.class);
                    return new OptionDefinition(
                            option.name(),
                            option.description(),
                            inferType(param.getType(), method),
                            option.required()
                    );
                })
                .toList();
    }

    private OptionType inferType(Class<?> type, Method method) {
        OptionType optionType = OPTION_TYPES.get(type);
        if (optionType == null) {
            throw new IllegalStateException(
                    "Unsupported @Option parameter type '" + type.getName() + "' in " + method);
        }
        return optionType;
    }
}
