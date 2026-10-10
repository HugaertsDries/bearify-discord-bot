package com.bearify.controller.music.domain;

public interface MusicPlayerEventListener {
    default void onReady() {}
    default void onFailed(String reason) {}
    default void onTimedOut() { onFailed("Request timed out"); }
    default void onNoPlayersAvailable() {}
    default void onTrackNotFound(String query) {}
    default void onTrackLoadFailed(String reason) {}
    default void onPaused() {}
    default void onResumed() {}
    default void onNothingToAdvance() {}
    default void onNothingToGoBack() {}
}
