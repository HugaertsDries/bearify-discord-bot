package com.bearify.controller.music.domain;

public interface MusicPlayer {
    void join(MusicPlayerJoinResultHandler handler);
    void stop();
}
