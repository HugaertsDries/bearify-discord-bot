package com.bearify.discord.api.interaction;

/**
 * Abstraction over a Discord command interaction.
 * Implementations wrap the underlying library's interaction object (e.g. JDA, Discord4J).
 */
public interface CommandInteraction {

    String getName();

    ReplyBuilder reply(String message);
}
