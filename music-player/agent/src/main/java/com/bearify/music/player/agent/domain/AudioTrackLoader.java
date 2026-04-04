package com.bearify.music.player.agent.domain;

import java.util.List;

/**
 * Abstraction over LavaPlayer's {@code AudioPlayerManager.loadItem()}. Decouples domain logic
 * from LavaPlayer types.
 */
public interface AudioTrackLoader {

    void load(String query, String requesterTag, AudioTrackLoadCallback callback);
    void search(String query, int limit, AudioTrackSearchCallback callback);

    interface AudioTrackLoadCallback {
        void trackLoaded(Track track);
        void playlistLoaded(List<Track> tracks);
        void noMatches();
        void loadFailed(String message);
    }

    interface AudioTrackSearchCallback {
        void searchResults(List<com.bearify.music.player.bridge.model.TrackMetadata> tracks);
        void noMatches();
        void loadFailed(String message);
    }
}
