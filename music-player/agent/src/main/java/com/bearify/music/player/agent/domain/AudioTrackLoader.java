package com.bearify.music.player.agent.domain;

/**
 * Abstraction over LavaPlayer's {@code AudioPlayerManager.loadItem()}. Decouples domain logic
 * from LavaPlayer types.
 */
public interface AudioTrackLoader {

    void load(String identifier, AudioTrackLoadCallback callback);

    interface AudioTrackLoadCallback {
        void trackLoaded(Track track);
        void playlistLoaded(Track firstTrack);
        void noMatches();
        void loadFailed(String message);
    }
}
