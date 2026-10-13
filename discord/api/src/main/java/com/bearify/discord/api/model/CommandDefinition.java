package com.bearify.discord.api.model;

import java.util.List;

public record CommandDefinition(
        String name,
        String description,
        List<OptionDefinition> options,
        List<SubcommandDefinition> subcommands,
        Type type
) {

    /** A slash command, or a message context-menu command, which has no description or options. */
    public enum Type {
        SLASH,
        MESSAGE
    }

    public static CommandDefinition command(String name, String description, List<OptionDefinition> options) {
        return new CommandDefinition(name, description, options, List.of(), Type.SLASH);
    }

    public static CommandDefinition group(String name, String description, List<SubcommandDefinition> definitions) {
        return new CommandDefinition(name, description, List.of(), definitions, Type.SLASH);
    }

    public static CommandDefinition message(String name) {
        return new CommandDefinition(name, "", List.of(), List.of(), Type.MESSAGE);
    }

    public CommandDefinition(String name, String description) {
        this(name, description, List.of(), List.of(), Type.SLASH);
    }
}
