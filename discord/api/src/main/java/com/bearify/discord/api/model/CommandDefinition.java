package com.bearify.discord.api.model;

import java.util.List;

public record CommandDefinition(
        String name,
        String description,
        List<OptionDefinition> options
) {}
