package com.bearify.discord.jda;

import com.bearify.discord.api.voice.VoiceSession;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;

class JdaVoiceSession implements VoiceSession {

    private final AudioChannel channel;
    private final AudioManager audioManager;

    JdaVoiceSession(AudioChannel channel, AudioManager audioManager) {
        this.channel = channel;
        this.audioManager = audioManager;
    }

    @Override
    public String getChannelId() {
        return channel.getId();
    }

    @Override
    public boolean isLonely() {
        return channel.getMembers().stream()
                .filter(m -> !m.getUser().isBot())
                .findAny()
                .isEmpty();
    }

    @Override
    public void leave() {
        audioManager.closeAudioConnection();
    }
}
