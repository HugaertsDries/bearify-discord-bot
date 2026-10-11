package com.bearify.discord.api.voice;

/**
 * Represents the bot's active voice connection within a specific guild.
 * Only present when the bot is actually connected to a voice channel.
 */
public interface VoiceSession {

    /** The ID of the voice channel the bot is connected to. */
    String getChannelId();

    /** Whether no non-bot users are present in the current channel. */
    boolean isLonely();

    /** Whether at least one non-bot member who is not deafened (self or server) is in the current channel. */
    boolean hasListeners();

    /** Leave the current voice channel. */
    void leave();
}
