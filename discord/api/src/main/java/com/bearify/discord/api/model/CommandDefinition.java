package com.bearify.discord.api.model;

import java.util.List;

public record CommandDefinition(
        String name,
        String description,
        List<OptionDefinition> options,
        List<SubcommandDefinition> subcommands
) {

    public static CommandDefinition command(String name, String description, List<OptionDefinition> options) {
        return new CommandDefinition(name, description, options, List.of());
    }

    public static CommandDefinition group(String name, String description, List<SubcommandDefinition> definitions) {
        return new CommandDefinition(name, description, List.of(), definitions);
    }

    public CommandDefinition(String name, String description) {
        this(name, description, List.of(), List.of());
    }
}
