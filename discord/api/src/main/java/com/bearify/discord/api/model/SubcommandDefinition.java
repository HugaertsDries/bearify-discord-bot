package com.bearify.discord.api.model;

import java.util.List;

public record SubcommandDefinition(String name, String description, List<OptionDefinition> options) {}
