package com.bearify.controller.music.domain;

public interface MusicPlayerEventHandler {

    default void onReady(MusicPlayerEvent.Ready event) {
    }
}
