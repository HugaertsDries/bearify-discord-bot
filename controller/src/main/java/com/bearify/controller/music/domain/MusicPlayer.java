package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.TrackRequest;

import java.time.Duration;
import java.util.Optional;

public interface MusicPlayer {
    void join(MusicPlayerEventListener handler);
    void stop(MusicPlayerEventListener handler);
    void play(TrackRequest request, MusicPlayerEventListener handler);
    void togglePause(String requesterTag, MusicPlayerEventListener handler);
    void previous(String requesterTag, MusicPlayerEventListener handler);
    void next(String requesterTag, MusicPlayerEventListener handler);
    /** {@link java.time.Duration#ZERO} means use the player's configured default. */
    void rewind(Duration seek, String requesterTag, MusicPlayerEventListener handler);
    /** {@link java.time.Duration#ZERO} means use the player's configured default. */
    void forward(Duration seek, String requesterTag, MusicPlayerEventListener handler);
    void clear(String requesterTag, MusicPlayerEventListener handler);
    /** Blocks for the bear's reply, a {@link MusicPlayerEvent.QueueSnapshot} or {@link MusicPlayerEvent.PlayerNotFound}; empty when it doesn't answer in time. */
    Optional<MusicPlayerEvent> queue();
}
