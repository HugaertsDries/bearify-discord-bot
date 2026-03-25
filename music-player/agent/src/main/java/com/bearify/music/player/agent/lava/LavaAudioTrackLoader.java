package com.bearify.music.player.agent.lava;

import com.bearify.music.player.agent.domain.AudioTrackLoader;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/**
 * Adapts LavaPlayer's {@link AudioPlayerManager#loadItem} to the domain {@link AudioTrackLoader}.
 */
class LavaAudioTrackLoader implements AudioTrackLoader {

    private final AudioPlayerManager playerManager;

    LavaAudioTrackLoader(AudioPlayerManager playerManager) {
        this.playerManager = playerManager;
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
                AudioTrack first = playlist.getSelectedTrack() != null
                        ? playlist.getSelectedTrack()
                        : playlist.getTracks().getFirst();
                first.setUserData(requesterTag);
                callback.playlistLoaded(new LavaTrack(first, requesterTag));
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
