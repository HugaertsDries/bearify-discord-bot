package com.bearify.controller.music.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.controller.music.domain.MusicPlayerTextChannelRegistry;
import com.bearify.controller.music.port.MusicPlayerTrackAnnouncer;
import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import org.springframework.stereotype.Component;

@Component
public class TextChannelMusicPlayerTrackAnnouncer implements MusicPlayerTrackAnnouncer {

    private final DiscordClient discord;
    private final MusicPlayerTextChannelRegistry textChannelRegistry;

    public TextChannelMusicPlayerTrackAnnouncer(DiscordClient discord,
                                                MusicPlayerTextChannelRegistry textChannelRegistry) {
        this.discord = discord;
        this.textChannelRegistry = textChannelRegistry;
    }

    @Override
    public void announce(MusicPlayerEvent event) {
        if (event instanceof MusicPlayerEvent.TrackStart ts) {
            textChannelRegistry.find(ts.guildId()).ifPresent(channelId ->
                    discord.textChannel(channelId)
                            .send(BearifyEmoji.MUSIC + " Now playing: **" + ts.track().title() + "** by " + ts.track().author()));
        }
    }
}
