package com.bearify.controller.music.discord;

import com.bearify.controller.music.domain.PlayerMessageStore;
import com.bearify.discord.api.gateway.DiscordClient;
import org.springframework.stereotype.Component;

@Component
public class DiscordPlaybackAnnouncerFactory {

    private final DiscordClient discord;
    private final AnnouncerProperties properties;
    private final PlayerMessageStore store;

    public DiscordPlaybackAnnouncerFactory(DiscordClient discord, AnnouncerProperties properties, PlayerMessageStore store) {
        this.discord = discord;
        this.properties = properties;
        this.store = store;
    }

    public DiscordPlaybackAnnouncer create(String playerId, String guildId, String textChannelId) {
        return new DiscordPlaybackAnnouncer(discord, properties, store, playerId, guildId, textChannelId);
    }
}
