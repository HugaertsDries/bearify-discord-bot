package com.bearify.controller.music.domain;

public interface MusicPlayerJoinResultHandler {
    void onReady();
    void onFailed(String reason);
}
