package com.bearify.discord.api.interaction;

import java.util.Optional;

/**
 * Abstraction over a Discord command interaction.
 * Implementations wrap the underlying library's interaction object (e.g. JDA, Discord4J).
 */
public interface CommandInteraction {

    EditableMessage defer();

    ReplyBuilder reply(String message);

    String getName();

    Optional<String> getOption(String name);
}
