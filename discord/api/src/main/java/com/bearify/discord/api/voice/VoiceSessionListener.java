package com.bearify.discord.api.voice;

@FunctionalInterface
public interface VoiceSessionListener {
    void onJoined(String channelId);
}
