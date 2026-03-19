package com.bearify.discord.api.gateway;

import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.discord.api.voice.VoiceSessionListener;

import java.util.Optional;

/**
 * Guild-scoped Discord capabilities.
 */
public interface Guild {

    /** The bot's current voice session for this guild, or empty if not connected. */
    Optional<VoiceSession> voice();

    /** Join a voice channel and invoke the listener once connected. */
    void join(String channelId, VoiceSessionListener onJoined);
}
