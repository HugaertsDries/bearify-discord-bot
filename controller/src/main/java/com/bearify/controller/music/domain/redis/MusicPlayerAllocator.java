package com.bearify.controller.music.domain.redis;

import java.util.Optional;

public interface MusicPlayerAllocator {

    Optional<String> findAssignedTo(String guildId, String voiceChannelId);

    Optional<String> claim();

    boolean assign(String guildId, String voiceChannelId, String playerId);

    void release(String playerId);
}
