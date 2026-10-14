package com.bearify.discord.jda;

import com.bearify.discord.api.format.TextCut;
import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.discord.api.voice.VoiceSession;
import net.dv8tion.jda.api.entities.channel.attribute.IVoiceStatusChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.exceptions.PermissionException;
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
    public boolean hasListeners() {
        // a missing voice state (cache off) counts as listening, which keeps the bear playing
        return channel.getMembers().stream()
                .filter(m -> !m.getUser().isBot())
                .anyMatch(m -> m.getVoiceState() == null || !m.getVoiceState().isDeafened());
    }

    @Override
    public boolean setStatus(String status) {
        // a stage channel has no status
        if (!(channel instanceof IVoiceStatusChannel statusChannel)) {
            return false;
        }
        String truncated = TextCut.cut(status, IVoiceStatusChannel.MAX_STATUS_LENGTH);
        try {
            statusChannel.modifyStatus(truncated).queue(null, error ->
                    LOG.warn("Failed to set the status of voice channel {}: {}", channel.getId(), error.getMessage()));
            return true;
        } catch (PermissionException e) {
            // JDA checks Set Voice Channel Status up front, and Manage Channels once the bot has left the channel
            return false;
        }
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
