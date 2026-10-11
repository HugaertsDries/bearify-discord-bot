package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.StopReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class MusicPlayerAnnouncementConsumer implements MusicPlayerEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(MusicPlayerAnnouncementConsumer.class);

    private final MusicPlayerAnnouncementRegistry registry;

    public MusicPlayerAnnouncementConsumer(MusicPlayerAnnouncementRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void accept(MusicPlayerEvent event) {
        for (MusicPlayerEventConsumer announcer : registry.findAll(event.playerId(), event.guildId())) {
            try {
                announcer.accept(event);
            } catch (Exception e) {
                LOG.warn("Failed to deliver event {} for player {}", event.getClass().getSimpleName(), event.playerId(), e);
            }
        }
        // kept after QueueEmpty, so an idle leave still has a channel to post its note in
        // each announcer forgets the message it deleted itself, so one posted by a concurrent TrackStart stays remembered
        if (event instanceof MusicPlayerEvent.Stopped) {
            registry.removeAll(event.playerId(), event.guildId());
        }
    }

    /** The bear vanished without a Stopped (killed or crashed): end its session as if it had sent one. */
    public void lost(String playerId, String guildId) {
        accept(new MusicPlayerEvent.Stopped(playerId, UUID.randomUUID().toString(), guildId, StopReason.LOST));
    }
}
