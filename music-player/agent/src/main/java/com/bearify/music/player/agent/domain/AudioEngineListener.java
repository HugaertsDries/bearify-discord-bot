package com.bearify.music.player.agent.domain;

/**
 * Listener for audio engine lifecycle events. Decouples domain logic from LavaPlayer's event adapter.
 */
public interface AudioEngineListener {
    void onTrackStart(Track track);

    void onTrackEnd(Track track, boolean mayStartNext);

    void onTrackError(Track track, String message);

    void onTrackStuck(Track track, long thresholdMs);

}
