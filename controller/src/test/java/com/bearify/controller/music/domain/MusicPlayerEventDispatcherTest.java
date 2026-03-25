package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerEventDispatcherTest {

    @Test
    void dispatchSendsEventToAllConsumers() {
        AtomicReference<MusicPlayerEvent> first = new AtomicReference<>();
        AtomicReference<MusicPlayerEvent> second = new AtomicReference<>();
        MusicPlayerEventDispatcher dispatcher = new MusicPlayerEventDispatcher(List.of(first::set, second::set));
        MusicPlayerEvent event = new MusicPlayerEvent.QueueEmpty("player-1", "req-1", "guild-1");

        dispatcher.dispatch(event);

        assertThat(first.get()).isSameAs(event);
        assertThat(second.get()).isSameAs(event);
    }

    @Test
    void dispatchStillCallsTrackAnnouncerWhenPendingInteractionMatches() {
        MusicPlayerPendingInteractions interactions = new MusicPlayerPendingInteractions();
        MusicPlayerPendingInteractions.PendingInteraction pending = interactions.register();
        AtomicReference<MusicPlayerEvent> announced = new AtomicReference<>();
        MusicPlayerEventDispatcher dispatcher = new MusicPlayerEventDispatcher(List.of(interactions, announced::set));
        MusicPlayerEvent event = new MusicPlayerEvent.Ready("player-1", pending.requestId());

        dispatcher.dispatch(event);

        assertThat(pending.future().join()).isEqualTo(event);
        assertThat(announced.get()).isSameAs(event);
    }
}
