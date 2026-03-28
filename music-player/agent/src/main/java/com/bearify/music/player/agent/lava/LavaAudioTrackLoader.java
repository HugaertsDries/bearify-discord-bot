package com.bearify.music.player.agent.lava;

import com.bearify.music.player.agent.domain.AudioTrackLoader;
import com.bearify.music.player.agent.domain.Track;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;

/**
 * Adapts LavaPlayer's {@link AudioPlayerManager#loadItem} to the domain {@link AudioTrackLoader}.
 *
 * <p>Routing:
 * <ul>
 *   <li>Single track → {@code callback.trackLoaded}</li>
 *   <li>Search result (isSearchResult) → {@code callback.trackLoaded} with the first result</li>
 *   <li>Real playlist → {@code callback.playlistLoaded} with all tracks from the selected track
 *       to end, capped at {@code maxTracks}</li>
 * </ul>
 */
class LavaAudioTrackLoader implements AudioTrackLoader {

    private final AudioPlayerManager playerManager;
    private final int maxTracks;

    LavaAudioTrackLoader(AudioPlayerManager playerManager, int maxTracks) {
        this.playerManager = playerManager;
        this.maxTracks = maxTracks;
    }

    @Override
    public void load(String query, String requesterTag, AudioTrackLoadCallback callback) {
        playerManager.loadItem(query, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                track.setUserData(requesterTag);
                callback.trackLoaded(new LavaTrack(track, requesterTag));
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.isSearchResult()) {
                    AudioTrack first = playlist.getTracks().getFirst();
                    first.setUserData(requesterTag);
                    callback.trackLoaded(new LavaTrack(first, requesterTag));
                    return;
                }
                List<AudioTrack> all = playlist.getTracks();
                int startIndex = 0;
                AudioTrack selected = playlist.getSelectedTrack();
                if (selected != null) {
                    int idx = all.indexOf(selected);
                    if (idx >= 0) startIndex = idx;
                }
                List<Track> tracks = all.subList(startIndex, all.size()).stream()
                        .limit(maxTracks)
                        .<Track>map(t -> {
                            t.setUserData(requesterTag);
                            return new LavaTrack(t, requesterTag);
                        })
                        .toList();
                callback.playlistLoaded(tracks);
            }

            @Override
            public void noMatches() {
                callback.noMatches();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                callback.loadFailed(exception.getMessage());
            }
        });
    }
}
