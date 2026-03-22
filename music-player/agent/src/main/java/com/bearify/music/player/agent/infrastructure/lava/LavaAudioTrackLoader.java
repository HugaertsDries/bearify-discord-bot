package com.bearify.music.player.agent.infrastructure.lava;

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
    public void load(String identifier, AudioTrackLoadCallback callback) {
        playerManager.loadItem(identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                callback.trackLoaded(new LavaTrack(track));
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack first = playlist.getSelectedTrack() != null
                        ? playlist.getSelectedTrack()
                        : playlist.getTracks().getFirst();
                callback.playlistLoaded(new LavaTrack(first));
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
