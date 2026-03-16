package com.bearify.music.player.agent.domain;

public interface VoiceConnectionManager {
    void connect(ConnectionRequest request);
    void disconnect(String guildId);
}
