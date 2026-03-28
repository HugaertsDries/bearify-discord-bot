package com.bearify.discord.api.gateway;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.model.CommandDefinition;

import java.util.List;
import java.util.function.Consumer;

/**
 * Creates a fully configured {@link DiscordClient}.
 * Commands and the interaction handler are provided up front so no partially
 * configured client can exist.
 */
public interface DiscordClientFactory {

    DiscordClient create(List<CommandDefinition> commands,
                         Consumer<CommandInteraction> handler);

    DiscordClient create(List<CommandDefinition> commands,
                         Consumer<CommandInteraction> handler,
                         Activity activity);
}
