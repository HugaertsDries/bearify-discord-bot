package com.bearify.controller.music.domain;

import java.util.Optional;

public interface MusicPlayerPool {
    Optional<MusicPlayer> acquire(String guildId, String voiceChannelId);
}
