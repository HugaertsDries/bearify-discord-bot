package com.bearify.discord.api.model;

public record OptionDefinition(
        String name,
        String description,
        OptionType type,
        boolean required
) {
    public enum OptionType {
        STRING,
        INTEGER,
        BOOLEAN
    }
}
