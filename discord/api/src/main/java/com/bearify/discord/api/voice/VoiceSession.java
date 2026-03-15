package com.bearify.discord.api.voice;

/**
 * Represents the bot's voice connection within a specific guild.
 */
public interface VoiceSession {

    /** Request that the bot joins the given voice channel. */
    void joinChannel(String channelId);

    /** Leave the current voice channel in this guild. */
    void leave();

    /** Whether the bot is currently connected to voice in this guild. */
    boolean isConnected();

    /** The guild this session belongs to. */
    String guildId();

    /** Register a listener invoked when the bot joins a voice channel in this guild. */
    void onJoined(VoiceSessionListener listener);
}
