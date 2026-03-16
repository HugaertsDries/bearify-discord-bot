package com.bearify.discord.api.voice;

import java.util.Optional;

/**
 * Represents the bot's voice connection within a specific guild.
 */
public interface VoiceSession {

    /** Join a voice channel and invoke the listener once connected. */
    void join(String channelId, VoiceSessionListener onJoined);

    /** Leave the current voice channel in this guild. */
    void leave();

    /** The guild this session belongs to. */
    String getGuildId();

    /** The ID of the voice channel the bot is currently connected to, empty if not connected. */
    Optional<String> getConnectedChannelId();

    /** Whether no other non-bot human users are present in the current channel. */
    boolean isAlone();
}
