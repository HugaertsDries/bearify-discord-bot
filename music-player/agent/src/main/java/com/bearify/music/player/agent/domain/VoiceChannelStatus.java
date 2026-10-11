package com.bearify.music.player.agent.domain;

import com.bearify.discord.api.gateway.DiscordClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shows the playing track as the voice channel status of one guild's player; a failure never reaches playback. */
public class VoiceChannelStatus {

    private static final Logger LOG = LoggerFactory.getLogger(VoiceChannelStatus.class);

    private final DiscordClient client;
    private final String guildId;
    private volatile boolean disabled = false;

    public VoiceChannelStatus(DiscordClient client, String guildId) {
        this.client = client;
        this.guildId = guildId;
    }

    public void show(Track track) {
        apply("🎶 " + track.title() + " — " + track.author());
    }

    public void clear() {
        apply("");
    }

    private void apply(String status) {
        // one warning per session is enough, and a rejected status stays rejected
        if (disabled) {
            return;
        }
        try {
            client.guild(guildId).voice().ifPresent(session -> {
                if (!session.setStatus(status)) {
                    disabled = true;
                    LOG.warn("Can't set voice channel status in guild {} (missing Set Voice Channel Status permission or unsupported channel); skipping for this session", guildId);
                }
            });
        } catch (RuntimeException e) {
            LOG.warn("Failed to set voice channel status in guild {}", guildId, e);
        }
    }
}
