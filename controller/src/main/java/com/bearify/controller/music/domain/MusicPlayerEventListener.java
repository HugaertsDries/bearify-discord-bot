package com.bearify.controller.music.domain;

public interface MusicPlayerEventListener {
    default void onReady() {}
    default void onFailed(String reason) {}
    default void onNoPlayersAvailable() {}
    default void onTrackNotFound(String query) {}
    default void onTrackLoadFailed(String reason) {}
    default void onPaused() {}
    default void onResumed() {}
    default void onNothingToGoBack() {}
    default void onQueueEmpty() {}
}
