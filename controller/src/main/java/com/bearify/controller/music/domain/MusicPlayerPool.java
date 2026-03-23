package com.bearify.controller.music.domain;

import java.util.Optional;

public interface MusicPlayerPool {
    MusicPlayer acquire(String guildId, String voiceChannelId);
    Optional<MusicPlayer> find(String guildId, String voiceChannelId);
    boolean hasActiveSessionFor(String guildId);
}
