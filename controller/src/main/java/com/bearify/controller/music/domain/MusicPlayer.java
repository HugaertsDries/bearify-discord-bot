package com.bearify.controller.music.domain;

import com.bearify.shared.events.MusicPlayerEvent;

import java.util.concurrent.CompletableFuture;

public interface MusicPlayer {
    CompletableFuture<MusicPlayerEvent> join();
}
