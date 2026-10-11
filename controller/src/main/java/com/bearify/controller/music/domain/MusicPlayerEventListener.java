package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.model.TrackMetadata;

import java.time.Duration;

public interface MusicPlayerEventListener {
    default void onReady() {}
    default void onFailed(String reason) {}
    default void onTimedOut() { onFailed("Request timed out"); }
    default void onNoPlayersAvailable() {}
    default void onTrackQueued(TrackMetadata track, int queuedCount) {}
    default void onTrackNotFound(String query) {}
    default void onTrackLoadFailed(String reason) {}
    default void onPaused() {}
    default void onResumed() {}
    default void onSkipped() {}
    default void onWentBack() {}
    default void onRestarted() {}
    /** {@code seek} is what the player actually applied, also when its default was requested. */
    default void onRewound(Duration seek) {}
    /** {@code seek} is what the player actually applied, also when its default was requested. */
    default void onForwarded(Duration seek) {}
    default void onCleared() {}
    default void onRemoved(TrackMetadata track) {}
    default void onTrackNotInQueue() {}
    /** {@code count} is 0 when the queue was empty. */
    default void onShuffled(int count) {}
    default void onVolumeChanged(int volume) {}
    default void onStopped() {}
    default void onNothingPlaying() {}
    default void onNothingToAdvance() {}
    default void onNothingToGoBack() {}
    /** A rewind or forward on a track that can't seek, such as a live stream. */
    default void onNotSeekable() {}
}
