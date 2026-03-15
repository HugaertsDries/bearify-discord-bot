package com.bearify.controller.music.domain;

public sealed interface MusicPlayerEvent permits MusicPlayerEvent.Ready {

    void dispatchTo(MusicPlayerEventHandler handler);

    String playerId();

    record Ready(String playerId) implements MusicPlayerEvent {
        @Override
        public void dispatchTo(MusicPlayerEventHandler handler) {
            handler.onReady(this);
        }
    }
}
