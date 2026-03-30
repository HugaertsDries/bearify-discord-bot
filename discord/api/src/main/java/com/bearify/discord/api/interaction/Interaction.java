package com.bearify.discord.api.interaction;

import java.util.Optional;

public interface Interaction {

    Optional<String> getGuildId();

    Optional<String> getVoiceChannelId();

    Optional<String> getTextChannelId();

    default String getUserMention() {
        return "Someone";
    }
}
