package com.bearify.controller.music.discord;

import com.bearify.discord.api.gateway.DiscordClient;
import org.springframework.stereotype.Component;

@Component
public class DiscordPlaybackAnnouncerFactory {

    private final DiscordClient discord;
    private final AnnouncerProperties properties;

    public DiscordPlaybackAnnouncerFactory(DiscordClient discord, AnnouncerProperties properties) {
        this.discord = discord;
        this.properties = properties;
    }

    public DiscordPlaybackAnnouncer create(String textChannelId) {
        return new DiscordPlaybackAnnouncer(discord, properties, textChannelId);
    }
}
