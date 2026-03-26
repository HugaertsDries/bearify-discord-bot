package com.bearify.discord.api.interaction;

import java.util.Optional;

/**
 * Abstraction over a Discord command interaction.
 * Implementations wrap the underlying library's interaction object (e.g. JDA, Discord4J).
 */
public interface CommandInteraction {

    default EditableMessage defer() {
        return defer(false);
    }

    EditableMessage defer(boolean ephemeral);

    ReplyBuilder reply(String message);

    Optional<String> getOption(String name);

    String getName();

    Optional<String> getSubcommandName();

    Optional<String> getGuildId();

    Optional<String> getVoiceChannelId();

    Optional<String> getTextChannelId();

    default String getUserMention() {
        return "Someone";
    }

}
