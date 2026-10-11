package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.StopReason;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerAnnouncementConsumerTest {

    private static final String PLAYER_ID = "player-1";
    private static final String GUILD_A = "guild-a";
    private static final String GUILD_B = "guild-b";

    @Test
    void acceptFansOutEventToAllAnnouncersForPlayer() {
        MusicPlayerAnnouncementRegistry registry = new MusicPlayerAnnouncementRegistry();
        AtomicReference<MusicPlayerEvent> first = new AtomicReference<>();
        AtomicReference<MusicPlayerEvent> second = new AtomicReference<>();
        registry.subscribe(PLAYER_ID, GUILD_A, first::set);
        registry.subscribe(PLAYER_ID, GUILD_A, second::set);
        MusicPlayerAnnouncementConsumer consumer = new MusicPlayerAnnouncementConsumer(registry);
        MusicPlayerEvent event = new MusicPlayerEvent.QueueEmpty(PLAYER_ID, "req-1", GUILD_A);

        consumer.accept(event);

        assertThat(first.get()).isSameAs(event);
        assertThat(second.get()).isSameAs(event);
    }

    @Test
    void keepsAnnouncerRegisteredAfterQueueEmpty() {
        MusicPlayerAnnouncementRegistry registry = new MusicPlayerAnnouncementRegistry();
        registry.subscribe(PLAYER_ID, GUILD_A, event -> {});
        MusicPlayerAnnouncementConsumer consumer = new MusicPlayerAnnouncementConsumer(registry);

        consumer.accept(new MusicPlayerEvent.QueueEmpty(PLAYER_ID, "req-1", GUILD_A));

        assertThat(registry.findAll(PLAYER_ID, GUILD_A)).hasSize(1);
    }

    @Test
    void removesAnnouncersWhenStopped() {
        MusicPlayerAnnouncementRegistry registry = new MusicPlayerAnnouncementRegistry();
        List<MusicPlayerEvent> delivered = new ArrayList<>();
        registry.subscribe(PLAYER_ID, GUILD_A, delivered::add);
        MusicPlayerAnnouncementConsumer consumer = new MusicPlayerAnnouncementConsumer(registry);
        MusicPlayerEvent stopped = new MusicPlayerEvent.Stopped(PLAYER_ID, "req-1", GUILD_A, StopReason.IDLE);

        consumer.accept(stopped);

        assertThat(delivered).containsExactly(stopped);
        assertThat(registry.findAll(PLAYER_ID, GUILD_A)).isEmpty();
    }

    @Test
    void acceptContinuesWhenOneAnnouncerThrows() {
        MusicPlayerAnnouncementRegistry registry = new MusicPlayerAnnouncementRegistry();
        AtomicInteger delivered = new AtomicInteger();
        registry.subscribe(PLAYER_ID, GUILD_A, event -> {
            throw new IllegalStateException("boom");
        });
        registry.subscribe(PLAYER_ID, GUILD_A, event -> delivered.incrementAndGet());
        MusicPlayerAnnouncementConsumer consumer = new MusicPlayerAnnouncementConsumer(registry);

        consumer.accept(new MusicPlayerEvent.Stopped(PLAYER_ID, "req-1", GUILD_A, StopReason.REQUESTED));

        assertThat(delivered.get()).isEqualTo(1);
    }

    @Test
    void deliversEventsOnlyToAnnouncersOfTheEventsGuild() {
        MusicPlayerAnnouncementRegistry registry = new MusicPlayerAnnouncementRegistry();
        List<MusicPlayerEvent> guildA = new ArrayList<>();
        List<MusicPlayerEvent> guildB = new ArrayList<>();
        registry.subscribe(PLAYER_ID, GUILD_A, guildA::add);
        registry.subscribe(PLAYER_ID, GUILD_B, guildB::add);
        MusicPlayerAnnouncementConsumer consumer = new MusicPlayerAnnouncementConsumer(registry);
        MusicPlayerEvent event = new MusicPlayerEvent.NothingToAdvance(PLAYER_ID, "req-1", GUILD_A);

        consumer.accept(event);

        assertThat(guildA).containsExactly(event);
        assertThat(guildB).isEmpty();
    }

    @Test
    void stoppingOneGuildKeepsOtherGuildsAnnouncers() {
        MusicPlayerAnnouncementRegistry registry = new MusicPlayerAnnouncementRegistry();
        List<MusicPlayerEvent> guildB = new ArrayList<>();
        registry.subscribe(PLAYER_ID, GUILD_A, event -> {});
        registry.subscribe(PLAYER_ID, GUILD_B, guildB::add);
        MusicPlayerAnnouncementConsumer consumer = new MusicPlayerAnnouncementConsumer(registry);
        MusicPlayerEvent guildBEvent = new MusicPlayerEvent.NothingToAdvance(PLAYER_ID, "req-2", GUILD_B);

        consumer.accept(new MusicPlayerEvent.Stopped(PLAYER_ID, "req-1", GUILD_A, StopReason.REQUESTED));
        consumer.accept(guildBEvent);

        assertThat(registry.findAll(PLAYER_ID, GUILD_A)).isEmpty();
        assertThat(guildB).containsExactly(guildBEvent);
    }

    @Test
    void endsLostSessionAsStoppedWithLostReason() {
        MusicPlayerAnnouncementRegistry registry = new MusicPlayerAnnouncementRegistry();
        List<MusicPlayerEvent> delivered = new ArrayList<>();
        registry.subscribe(PLAYER_ID, GUILD_A, delivered::add);
        MusicPlayerAnnouncementConsumer consumer = new MusicPlayerAnnouncementConsumer(registry);

        consumer.lost(PLAYER_ID, GUILD_A);

        assertThat(delivered).singleElement().isInstanceOfSatisfying(MusicPlayerEvent.Stopped.class,
                stopped -> assertThat(stopped.reason()).isEqualTo(StopReason.LOST));
        assertThat(registry.findAll(PLAYER_ID, GUILD_A)).isEmpty();
    }
}
