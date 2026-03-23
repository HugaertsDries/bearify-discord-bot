package com.bearify.music.player.agent.port;

import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.music.player.agent.RecordingVoiceConnectionManager;
import com.bearify.music.player.agent.config.PlayerProperties;
import com.bearify.music.player.agent.domain.AudioEngine;
import com.bearify.music.player.agent.domain.AudioEngineListener;
import com.bearify.music.player.agent.domain.AudioPlayer;
import com.bearify.music.player.agent.domain.AudioPlayerPool;
import com.bearify.music.player.agent.domain.AudioTrackLoader;
import com.bearify.music.player.agent.domain.Track;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerInteractionDispatcherTest {

    private static final String PLAYER_ID = "player-1";
    private static final String REQUEST_ID = "req-1";
    private static final String VOICE_CHANNEL_ID = "voice-1";
    private static final String GUILD_ID = "guild-1";
    private static final String TEXT_CHANNEL_ID = "text-1";
    private static final PlayerProperties PROPS = new PlayerProperties(
            Duration.ofSeconds(3), Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(5),
            new PlayerProperties.Assignment(Duration.ofSeconds(30), Duration.ofSeconds(10)));

    @Test
    void connectsVoiceManagerWhenConnectInteractionIsHandled() {
        RecordingVoiceConnectionManager voiceConnectionManager = new RecordingVoiceConnectionManager();
        StubAudioPlayerPool pool = new StubAudioPlayerPool();
        MusicPlayerInteractionDispatcher dispatcher = new MusicPlayerInteractionDispatcher(voiceConnectionManager, pool, null, PLAYER_ID);

        dispatcher.handle(new MusicPlayerInteraction.Connect(PLAYER_ID, REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));

        assertThat(voiceConnectionManager.getCalls()).hasSize(1);
        assertThat(voiceConnectionManager.getCalls().getFirst().requestId()).isEqualTo(REQUEST_ID);
        assertThat(voiceConnectionManager.getCalls().getFirst().voiceChannelId()).isEqualTo(VOICE_CHANNEL_ID);
        assertThat(voiceConnectionManager.getCalls().getFirst().guildId()).isEqualTo(GUILD_ID);
        assertThat(voiceConnectionManager.getDisconnectedGuilds()).isEmpty();
    }

    @Test
    void disconnectsWhenStopInteractionIsHandled() {
        RecordingVoiceConnectionManager voiceConnectionManager = new RecordingVoiceConnectionManager();
        StubAudioPlayerPool pool = new StubAudioPlayerPool();
        MusicPlayerInteractionDispatcher dispatcher = new MusicPlayerInteractionDispatcher(voiceConnectionManager, pool, null, PLAYER_ID);

        dispatcher.handle(new MusicPlayerInteraction.Stop(PLAYER_ID, REQUEST_ID, GUILD_ID));

        assertThat(voiceConnectionManager.getDisconnectedGuilds()).containsExactly(GUILD_ID);
    }

    @Test
    void routesPlayQueryToLoader() {
        StubAudioPlayerPool pool = new StubAudioPlayerPool();
        MusicPlayerInteractionDispatcher dispatcher = new MusicPlayerInteractionDispatcher(
                new RecordingVoiceConnectionManager(), pool, null, PLAYER_ID);

        dispatcher.handle(new MusicPlayerInteraction.Play(PLAYER_ID, REQUEST_ID, TEXT_CHANNEL_ID, "bohemian rhapsody", GUILD_ID));

        assertThat(pool.loaderCalls).containsExactly("bohemian rhapsody");
    }

    @Test
    void routesTogglePauseToPlayer() {
        StubAudioPlayerPool pool = new StubAudioPlayerPool();
        pool.primeGuild(GUILD_ID);
        MusicPlayerInteractionDispatcher dispatcher = new MusicPlayerInteractionDispatcher(
                new RecordingVoiceConnectionManager(), pool, null, PLAYER_ID);

        dispatcher.handle(new MusicPlayerInteraction.TogglePause(PLAYER_ID, REQUEST_ID, GUILD_ID));

        assertThat(pool.togglePauseCalls).containsExactly(GUILD_ID);
    }

    @Test
    void routesNextToPlayer() {
        StubAudioPlayerPool pool = new StubAudioPlayerPool();
        pool.primeGuild(GUILD_ID);
        MusicPlayerInteractionDispatcher dispatcher = new MusicPlayerInteractionDispatcher(
                new RecordingVoiceConnectionManager(), pool, null, PLAYER_ID);

        dispatcher.handle(new MusicPlayerInteraction.Next(PLAYER_ID, REQUEST_ID, GUILD_ID));

        assertThat(pool.nextCalls).containsExactly(GUILD_ID);
    }

    @Test
    void routesPreviousToPlayer() {
        StubAudioPlayerPool pool = new StubAudioPlayerPool();
        pool.primeGuild(GUILD_ID);
        MusicPlayerInteractionDispatcher dispatcher = new MusicPlayerInteractionDispatcher(
                new RecordingVoiceConnectionManager(), pool, null, PLAYER_ID);

        dispatcher.handle(new MusicPlayerInteraction.Previous(PLAYER_ID, REQUEST_ID, GUILD_ID));

        assertThat(pool.previousCalls).containsExactly(GUILD_ID);
    }

    @Test
    void routesRewindToPlayer() {
        StubAudioPlayerPool pool = new StubAudioPlayerPool();
        pool.primeGuild(GUILD_ID);
        MusicPlayerInteractionDispatcher dispatcher = new MusicPlayerInteractionDispatcher(
                new RecordingVoiceConnectionManager(), pool, null, PLAYER_ID);

        dispatcher.handle(new MusicPlayerInteraction.Rewind(PLAYER_ID, REQUEST_ID, GUILD_ID, 15000));

        assertThat(pool.rewindCalls).containsExactly("15000|" + GUILD_ID);
    }

    @Test
    void routesForwardToPlayer() {
        StubAudioPlayerPool pool = new StubAudioPlayerPool();
        pool.primeGuild(GUILD_ID);
        MusicPlayerInteractionDispatcher dispatcher = new MusicPlayerInteractionDispatcher(
                new RecordingVoiceConnectionManager(), pool, null, PLAYER_ID);

        dispatcher.handle(new MusicPlayerInteraction.Forward(PLAYER_ID, REQUEST_ID, GUILD_ID, 30000));

        assertThat(pool.forwardCalls).containsExactly("30000|" + GUILD_ID);
    }

    // --- STUB ---

    private final class StubAudioPlayerPool extends AudioPlayerPool {

        final List<String> loaderCalls = new ArrayList<>();
        final List<String> togglePauseCalls = new ArrayList<>();
        final List<String> nextCalls = new ArrayList<>();
        final List<String> previousCalls = new ArrayList<>();
        final List<String> rewindCalls = new ArrayList<>();
        final List<String> forwardCalls = new ArrayList<>();

        private final Set<String> primedGuilds = new HashSet<>();

        StubAudioPlayerPool() {
            super(null, null, "test");
        }

        void primeGuild(String guildId) {
            primedGuilds.add(guildId);
        }

        @Override
        public AudioPlayer getOrCreate(String guildId) {
            primedGuilds.add(guildId);
            return new StubPlayer(guildId);
        }

        @Override
        public AudioTrackLoader getLoader(String guildId) {
            return (query, cb) -> loaderCalls.add(query);
        }

        @Override
        public Optional<AudioPlayer> get(String guildId) {
            if (primedGuilds.contains(guildId)) {
                return Optional.of(new StubPlayer(guildId));
            }
            return Optional.empty();
        }

        private final class StubPlayer extends AudioPlayer {

            private final String guildId;

            StubPlayer(String guildId) {
                super(new NoOpEngine(), new NoOpAudioProvider(), null, PROPS, "test", guildId, () -> {});
                this.guildId = guildId;
            }

            @Override
            public void togglePause(String requestId) {
                togglePauseCalls.add(guildId);
            }

            @Override
            public void next(String requestId) {
                nextCalls.add(guildId);
            }

            @Override
            public void previous(String requestId) {
                previousCalls.add(guildId);
            }

            @Override
            public void rewind(Duration seek) {
                rewindCalls.add(seek.toMillis() + "|" + guildId);
            }

            @Override
            public void forward(Duration seek, String requestId) {
                forwardCalls.add(seek.toMillis() + "|" + guildId);
            }
        }

        private static final class NoOpEngine implements AudioEngine {
            @Override public Track getPlayingTrack() { return null; }
            @Override public void play(Track t) {}
            @Override public boolean isPaused() { return false; }
            @Override public void setPaused(boolean p) {}
            @Override public void addListener(AudioEngineListener l) {}
        }

        private static final class NoOpAudioProvider implements AudioProvider {
            @Override public boolean canProvide() { return false; }
            @Override public byte[] provide20MsAudio() { return new byte[0]; }
            @Override public boolean isOpus() { return false; }
        }
    }
}
