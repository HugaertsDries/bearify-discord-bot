package com.bearify.music.player.agent.domain;

import java.util.Optional;

/**
 * Abstraction over a LavaPlayer audio player. Decouples domain logic from LavaPlayer types.
 */
public interface AudioEngine {

    void play(Track track);

    default void destroy() {}

    Optional<Track> getPlayingTrack();

    boolean isPaused();

    void setPaused(boolean paused);

    int getVolume();

    void setVolume(int volume);

    void addListener(AudioEngineListener listener);

}
