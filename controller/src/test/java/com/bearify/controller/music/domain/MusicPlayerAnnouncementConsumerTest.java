package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerAnnouncementConsumerTest {

    @Test
    void acceptFansOutEventToAllAnnouncersForPlayer() {
        MusicPlayerAnnouncementRegistry registry = new MusicPlayerAnnouncementRegistry();
        AtomicReference<MusicPlayerEvent> first = new AtomicReference<>();
        AtomicReference<MusicPlayerEvent> second = new AtomicReference<>();
        registry.subscribe("player-1", first::set);
        registry.subscribe("player-1", second::set);
        MusicPlayerAnnouncementConsumer consumer = new MusicPlayerAnnouncementConsumer(registry);
        MusicPlayerEvent event = new MusicPlayerEvent.QueueEmpty("player-1", "req-1", "guild-1");

        consumer.accept(event);

        assertThat(first.get()).isSameAs(event);
        assertThat(second.get()).isSameAs(event);
    }

    @Test
    void acceptRemovesAllSubscriptionsAfterQueueEmpty() {
        MusicPlayerAnnouncementRegistry registry = new MusicPlayerAnnouncementRegistry();
        registry.subscribe("player-1", event -> {});
        MusicPlayerAnnouncementConsumer consumer = new MusicPlayerAnnouncementConsumer(registry);

        consumer.accept(new MusicPlayerEvent.QueueEmpty("player-1", "req-1", "guild-1"));

        assertThat(registry.findAll("player-1")).isEmpty();
    }

    @Test
    void acceptContinuesWhenOneAnnouncerThrows() {
        MusicPlayerAnnouncementRegistry registry = new MusicPlayerAnnouncementRegistry();
        AtomicInteger delivered = new AtomicInteger();
        registry.subscribe("player-1", event -> {
            throw new IllegalStateException("boom");
        });
        registry.subscribe("player-1", event -> delivered.incrementAndGet());
        MusicPlayerAnnouncementConsumer consumer = new MusicPlayerAnnouncementConsumer(registry);

        consumer.accept(new MusicPlayerEvent.Stopped("player-1", "req-1", "guild-1"));

        assertThat(delivered.get()).isEqualTo(1);
    }
}
