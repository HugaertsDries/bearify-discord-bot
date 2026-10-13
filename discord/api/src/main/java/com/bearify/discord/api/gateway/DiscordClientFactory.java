package com.bearify.discord.api.gateway;

import com.bearify.discord.api.interaction.Interaction;
import com.bearify.discord.api.model.CommandDefinition;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Creates a fully configured {@link DiscordClient}.
 * Commands and the interaction handler are provided up front so no partially
 * configured client can exist.
 */
public interface DiscordClientFactory {

    /**
     * @param ephemeralDeleteAfter how long after the last write an ephemeral reply deletes itself; empty keeps them
     */
    DiscordClient create(List<CommandDefinition> commands,
                         Consumer<Interaction> handler,
                         Optional<Activity> activity,
                         Optional<Duration> ephemeralDeleteAfter);
}
