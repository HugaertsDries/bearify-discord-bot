package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.model.TrackRequest;

import java.time.Duration;

public interface MusicPlayer {
    void join(MusicPlayerEventListener handler);
    void stop();
    void play(TrackRequest request, MusicPlayerEventListener handler);
    void togglePause(MusicPlayerEventListener handler);
    void previous(MusicPlayerEventListener handler);
    void next(MusicPlayerEventListener handler);
    /** {@link java.time.Duration#ZERO} means use the player's configured default. */
    void rewind(Duration seek);
    /** {@link java.time.Duration#ZERO} means use the player's configured default. */
    void forward(Duration seek, MusicPlayerEventListener handler);
    void clear();
}
