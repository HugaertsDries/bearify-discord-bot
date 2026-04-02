package com.bearify.controller.music.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerAnnouncementRegistryTest {

    @Test
    void subscribeStoresAnnouncerPerPlayer() {
        MusicPlayerAnnouncementRegistry registry = new MusicPlayerAnnouncementRegistry();
        PlaybackAnnouncer announcer = event -> {};

        registry.subscribe("player-1", announcer);

        assertThat(registry.findAll("player-1")).containsExactly(announcer);
    }

    @Test
    void subscribeIsIdempotentForSameAnnouncer() {
        MusicPlayerAnnouncementRegistry registry = new MusicPlayerAnnouncementRegistry();
        PlaybackAnnouncer announcer = event -> {};

        registry.subscribe("player-1", announcer);
        registry.subscribe("player-1", announcer);

        assertThat(registry.findAll("player-1")).containsExactly(announcer);
    }

    @Test
    void subscriptionsAreIsolatedPerPlayer() {
        MusicPlayerAnnouncementRegistry registry = new MusicPlayerAnnouncementRegistry();
        PlaybackAnnouncer announcer = event -> {};

        registry.subscribe("player-1", announcer);

        assertThat(registry.findAll("player-2")).isEmpty();
    }
}
