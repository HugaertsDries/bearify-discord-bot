package com.bearify.discord.api.interaction;

import java.util.Optional;

/**
 * Abstraction over a Discord command interaction.
 * Implementations wrap the underlying library's interaction object (e.g. JDA, Discord4J).
 */
public interface CommandInteraction extends RepliableInteraction {

    Optional<String> getOption(String name);

    String getName();

    Optional<String> getSubcommandName();

}
