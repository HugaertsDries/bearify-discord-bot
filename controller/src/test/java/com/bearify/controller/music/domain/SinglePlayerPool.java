package com.bearify.controller.music.domain;

import java.util.Optional;

/** A {@link MusicPlayerPool} where every channel already has {@code player}. */
public record SinglePlayerPool(MusicPlayer player) implements MusicPlayerPool {
    @Override public MusicPlayer acquire(String guildId, String voiceChannelId) { return player; }
    @Override public Optional<MusicPlayer> find(String guildId, String voiceChannelId) { return Optional.of(player); }
    @Override public boolean hasActiveSessionFor(String guildId) { return true; }
}
