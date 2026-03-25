package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;

import java.util.Collection;

public class MusicPlayerEventDispatcher {

    private final Collection<MusicPlayerEventConsumer> consumers;

    public MusicPlayerEventDispatcher(Collection<MusicPlayerEventConsumer> consumers) {
        this.consumers = consumers;
    }

    public void dispatch(MusicPlayerEvent event) {
        consumers.forEach(consumer -> consumer.accept(event));
    }
}
