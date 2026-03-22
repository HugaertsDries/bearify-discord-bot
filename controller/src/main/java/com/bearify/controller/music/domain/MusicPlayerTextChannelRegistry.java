package com.bearify.controller.music.domain;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MusicPlayerTextChannelRegistry {

    private final ConcurrentHashMap<String, String> guildToTextChannel = new ConcurrentHashMap<>();

    public void store(String guildId, String textChannelId) {
        guildToTextChannel.put(guildId, textChannelId);
    }

    public Optional<String> find(String guildId) {
        return Optional.ofNullable(guildToTextChannel.get(guildId));
    }

    public void remove(String guildId) {
        guildToTextChannel.remove(guildId);
    }
}
