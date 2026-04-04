package com.bearify.discord.api.model;

public record OptionDefinition(
        String name,
        String description,
        OptionType type,
        boolean required,
        boolean autocomplete
) {
    public enum OptionType {
        STRING,
        INTEGER,
        BOOLEAN
    }
}
