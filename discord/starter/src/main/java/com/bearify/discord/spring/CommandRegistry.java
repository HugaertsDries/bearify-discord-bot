package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.model.CommandDefinition;
import com.bearify.discord.api.model.OptionDefinition;
import com.bearify.discord.api.model.OptionDefinition.OptionType;
import com.bearify.discord.api.model.SubcommandDefinition;
import com.bearify.discord.spring.annotation.Command;
import com.bearify.discord.spring.annotation.Interaction;
import com.bearify.discord.spring.annotation.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;

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
    private final Map<String, CommandDefinition> definitions = new HashMap<>();

    private final CommandExceptionHandlerRegistry exceptionHandlerRegistry;

    CommandRegistry(CommandExceptionHandlerRegistry exceptionHandlerRegistry) {
        this.exceptionHandlerRegistry = exceptionHandlerRegistry;
    }

    void register(Interaction interaction, Object host, Method method) {
        Command command = AnnotationUtils.findAnnotation(AopUtils.getTargetClass(host), Command.class);

        if (command == null) {
            throw new IllegalStateException("Interaction " + interaction + " has no @Command annotation");
        }

        var isGrouped = !command.value().isBlank();

        var name = interaction.value();
        var key = isGrouped ? command.value() + "/" + name : name;

        if (handlers.containsKey(key)) {
            throw new IllegalStateException("Duplicate interaction '" + key + "' — already registered by: " + handlers.get(key));
        }

        handlers.put(key, new CommandHandler(host, method));

        List<OptionDefinition> options = introspectOptions(method);
        if (isGrouped) {
            definitions.computeIfAbsent(command.value(), group -> new CommandDefinition(group, command.description()));
            definitions.computeIfPresent(command.value(), (group, def) -> {
                var subcommand = new SubcommandDefinition(name, interaction.description(), options);
                var subcommands = new ArrayList<>(def.subcommands());
                subcommands.add(subcommand);
                return CommandDefinition.group(group, def.description(), subcommands);
            });
        } else {
            definitions.put(name, CommandDefinition.command(name, interaction.description(), options) );
        }

        LOG.info("Registered command '{}'", key);
    }

    public List<CommandDefinition> getDefinitions() {
        return definitions.values().stream().toList();
    }

    public void handle(CommandInteraction interaction) {
        String key = interaction.getSubcommandName().map(sub -> interaction.getName() + "/" + sub).orElseGet(interaction::getName);
        CommandHandler handler = handlers.get(key);
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

    private List<OptionDefinition> introspectOptions(Method method) {
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
