package com.bearify.controller.music.discord;

import com.bearify.discord.api.gateway.DiscordClient;
import org.springframework.stereotype.Component;

@Component
public class TextChannelMusicPlayerTrackAnnouncerFactory {

    private final DiscordClient discord;
    private final AnnouncerProperties properties;

    public TextChannelMusicPlayerTrackAnnouncerFactory(DiscordClient discord, AnnouncerProperties properties) {
        this.discord = discord;
        this.properties = properties;
    }

    public TextChannelMusicPlayerTrackAnnouncer create(String textChannelId) {
        return new TextChannelMusicPlayerTrackAnnouncer(discord, properties, textChannelId);
    }
}
