package com.bearify.controller.music.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerAnnouncementRegistryTest {

    @Test
    void subscribeStoresAnnouncerPerPlayerAndGuild() {
        MusicPlayerAnnouncementRegistry registry = new MusicPlayerAnnouncementRegistry();
        MusicPlayerEventConsumer announcer = event -> {};

        registry.subscribe("player-1", "guild-1", announcer);

        assertThat(registry.findAll("player-1", "guild-1")).containsExactly(announcer);
    }

    @Test
    void subscribeIsIdempotentForSameAnnouncer() {
        MusicPlayerAnnouncementRegistry registry = new MusicPlayerAnnouncementRegistry();
        MusicPlayerEventConsumer announcer = event -> {};

        registry.subscribe("player-1", "guild-1", announcer);
        registry.subscribe("player-1", "guild-1", announcer);

        assertThat(registry.findAll("player-1", "guild-1")).containsExactly(announcer);
    }

    @Test
    void subscriptionsAreIsolatedPerPlayer() {
        MusicPlayerAnnouncementRegistry registry = new MusicPlayerAnnouncementRegistry();
        MusicPlayerEventConsumer announcer = event -> {};

        registry.subscribe("player-1", "guild-1", announcer);

        assertThat(registry.findAll("player-2", "guild-1")).isEmpty();
    }

    @Test
    void subscriptionsAreIsolatedPerGuild() {
        MusicPlayerAnnouncementRegistry registry = new MusicPlayerAnnouncementRegistry();
        MusicPlayerEventConsumer announcer = event -> {};

        registry.subscribe("player-1", "guild-1", announcer);

        assertThat(registry.findAll("player-1", "guild-2")).isEmpty();
    }
}
