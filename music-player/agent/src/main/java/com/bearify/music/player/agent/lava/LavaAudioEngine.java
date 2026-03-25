package com.bearify.music.player.agent.lava;

import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.music.player.agent.domain.AudioEngine;
import com.bearify.music.player.agent.domain.AudioEngineListener;
import com.bearify.music.player.agent.domain.Track;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import dev.lavalink.youtube.YoutubeAudioSourceManager;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * LavaPlayer-backed implementation of {@link AudioEngine} and {@link AudioProvider}.
 * Owns the {@link AudioPlayerManager} and {@link AudioPlayer} lifecycle.
 */
public class LavaAudioEngine implements AudioEngine, AudioProvider {

    private final AudioPlayerManager playerManager;
    private final AudioPlayer audioPlayer;
    private final MutableAudioFrame frame = new MutableAudioFrame();
    private final ByteBuffer frameBuffer = ByteBuffer.allocate(4096);
    private byte[] audioData = new byte[0];

    public LavaAudioEngine(Optional<String> youtubeRefreshToken) {
        this.playerManager = new DefaultAudioPlayerManager();
        var source = new YoutubeAudioSourceManager();
        youtubeRefreshToken.ifPresentOrElse(
                token -> source.useOauth2(token, true),
                () -> source.useOauth2(null, false));
        playerManager.registerSourceManager(source);
        this.audioPlayer = playerManager.createPlayer();
        this.frame.setBuffer(frameBuffer);
    }

    /** Returns a loader backed by this engine's player manager. */
    public LavaAudioTrackLoader getLoader() {
        return new LavaAudioTrackLoader(playerManager);
    }

    // --- AudioEngine ---

    // TODO if track is nullable, return an optional
    @Override
    public Track getPlayingTrack() {
        AudioTrack track = audioPlayer.getPlayingTrack();
        if (track == null) return null;
        String requesterTag = track.getUserData(String.class);
        return new LavaTrack(track, requesterTag);
    }

    @Override
    public void play(Track track) {
        AudioTrack lavaTrack = unwrap(track);
        lavaTrack.setUserData(track.requesterTag());
        audioPlayer.playTrack(lavaTrack);
    }

    @Override
    public boolean isPaused() {
        return audioPlayer.isPaused();
    }

    @Override
    public void setPaused(boolean paused) {
        audioPlayer.setPaused(paused);
    }

    @Override
    public void addListener(AudioEngineListener listener) {
        audioPlayer.addListener(new AudioEventAdapter() {
            @Override
            public void onTrackStart(AudioPlayer player, AudioTrack track) {
                String requesterTag = track.getUserData(String.class);
                listener.onTrackStart(new LavaTrack(track, requesterTag));
            }

            @Override
            public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
                String requesterTag = track.getUserData(String.class);
                listener.onTrackEnd(new LavaTrack(track, requesterTag), endReason.mayStartNext);
            }

            @Override
            public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
                String requesterTag = track.getUserData(String.class);
                listener.onTrackError(new LavaTrack(track, requesterTag), exception.getMessage());
            }

            @Override
            public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
                String requesterTag = track.getUserData(String.class);
                listener.onTrackStuck(new LavaTrack(track, requesterTag), thresholdMs);
            }
        });
    }

    @Override
    public void destroy() {
        audioPlayer.stopTrack();
        playerManager.shutdown();
    }

    // --- AudioProvider ---

    @Override
    public boolean canProvide() {
        return audioPlayer.provide(frame);
    }

    @Override
    public byte[] provide20MsAudio() {
        int length = frame.getDataLength();
        if (audioData.length != length) {
            audioData = new byte[length];
        }
        frameBuffer.position(0);
        frameBuffer.get(audioData, 0, length);
        return audioData;
    }

    @Override
    public boolean isOpus() {
        return true;
    }

    // --- Helpers ---

    private static AudioTrack unwrap(Track handle) {
        if (handle instanceof LavaTrack lava) {
            return lava.unwrap();
        }
        throw new IllegalArgumentException("Expected LavaTrack but got: " + handle.getClass());
    }
}
