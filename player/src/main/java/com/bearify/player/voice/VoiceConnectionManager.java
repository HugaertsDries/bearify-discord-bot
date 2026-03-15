package com.bearify.player.voice;

public interface VoiceConnectionManager {
    void connect(String requestId, String voiceChannelId, String guildId);
    void disconnect();
}
