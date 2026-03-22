package com.bearify.discord.jda;

import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.discord.api.voice.VoiceSession;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;

class JdaVoiceSession implements VoiceSession {

    private static final Logger LOG = LoggerFactory.getLogger(JdaVoiceSession.class);

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
        if( audioManager.getSendingHandler() instanceof Closeable closeable) {
            try {
                closeable.close();
            } catch (IOException e) {
                LOG.warn("Failed to close audio provider on leave: {}", e.getMessage(), e);
            }
        }
        audioManager.closeAudioConnection();
    }
}
