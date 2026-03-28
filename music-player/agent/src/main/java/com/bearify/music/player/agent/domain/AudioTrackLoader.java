package com.bearify.music.player.agent.domain;

import java.util.List;

/**
 * Abstraction over LavaPlayer's {@code AudioPlayerManager.loadItem()}. Decouples domain logic
 * from LavaPlayer types.
 */
public interface AudioTrackLoader {

    void load(String query, String requesterTag, AudioTrackLoadCallback callback);

    interface AudioTrackLoadCallback {
        void trackLoaded(Track track);
        void playlistLoaded(List<Track> tracks);
        void noMatches();
        void loadFailed(String message);
    }
}
