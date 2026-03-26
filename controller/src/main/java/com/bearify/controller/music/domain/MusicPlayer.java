package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.model.TrackRequest;

import java.time.Duration;

public interface MusicPlayer {
    void join(MusicPlayerEventListener handler);
    void stop();
    void play(TrackRequest request, MusicPlayerEventListener handler);
    void togglePause(String requesterTag, MusicPlayerEventListener handler);
    void previous(String requesterTag, MusicPlayerEventListener handler);
    void next(String requesterTag, MusicPlayerEventListener handler);
    /** {@link java.time.Duration#ZERO} means use the player's configured default. */
    void rewind(Duration seek, String requesterTag);
    /** {@link java.time.Duration#ZERO} means use the player's configured default. */
    void forward(Duration seek, String requesterTag, MusicPlayerEventListener handler);
    void clear(String requesterTag);
}
