package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;

import java.util.concurrent.CompletableFuture;

public interface MusicPlayer {
    CompletableFuture<MusicPlayerEvent> join();
    void stop();
}
