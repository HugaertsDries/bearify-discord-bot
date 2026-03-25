package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MusicPlayerAnnouncementConsumer implements MusicPlayerEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(MusicPlayerAnnouncementConsumer.class);

    private final MusicPlayerAnnouncementRegistry registry;

    public MusicPlayerAnnouncementConsumer(MusicPlayerAnnouncementRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void accept(MusicPlayerEvent event) {
        for (MusicPlayerTrackAnnouncer announcer : registry.findAll(event.playerId())) {
            try {
                announcer.accept(event);
            } catch (Exception e) {
                LOG.warn("Failed to deliver event {} for player {}", event.getClass().getSimpleName(), event.playerId(), e);
            }
        }
        if (event instanceof MusicPlayerEvent.Stopped || event instanceof MusicPlayerEvent.QueueEmpty) {
            registry.removeAll(event.playerId());
        }
    }
}
