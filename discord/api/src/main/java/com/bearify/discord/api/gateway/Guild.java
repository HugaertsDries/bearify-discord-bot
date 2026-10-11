package com.bearify.discord.api.gateway;

import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.discord.api.voice.VoiceSessionListener;

import java.util.Optional;

/**
 * Guild-scoped Discord capabilities.
 */
public interface Guild {

    /** The bot's current voice session for this guild, or empty if not connected. */
    Optional<VoiceSession> voice();

    /** Whether the member with this user ID is in one of this guild's voice channels right now. */
    boolean isInVoice(String userId);

    /** Join a voice channel with the given audio provider and invoke the listener once connected. */
    void join(String channelId, AudioProvider provider, VoiceSessionListener onJoined);
}
