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
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.model.Request;
import com.bearify.music.player.bridge.model.TrackMetadata;
import com.bearify.music.player.bridge.model.TrackRequest;
import org.junit.jupiter.api.Test;
import com.bearify.music.player.agent.port.MusicPlayerEventDispatcher;

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
            Duration.ofSeconds(5),
            200,
            new PlayerProperties.Assignment(Duration.ofSeconds(30), Duration.ofSeconds(10)),
            new PlayerProperties.VoiceSession(Duration.ofSeconds(10), Duration.ofMinutes(5)),
            new PlayerProperties.Engine(new PlayerProperties.Engine.Youtube(null)));

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

        dispatcher.handle(new MusicPlayerInteraction.Play(PLAYER_ID, REQUEST_ID, GUILD_ID, new TrackRequest("bohemian rhapsody", TEXT_CHANNEL_ID, null)));

        assertThat(pool.loaderCalls).containsExactly("bohemian rhapsody");
    }

    @Test
    void routesTogglePauseToPlayer() {
        StubAudioPlayerPool pool = new StubAudioPlayerPool();
        pool.primeGuild(GUILD_ID);
        MusicPlayerInteractionDispatcher dispatcher = new MusicPlayerInteractionDispatcher(
                new RecordingVoiceConnectionManager(), pool, null, PLAYER_ID);

        dispatcher.handle(new MusicPlayerInteraction.TogglePause(PLAYER_ID, new Request(REQUEST_ID, "@user"), GUILD_ID));

        assertThat(pool.togglePauseCalls).containsExactly(GUILD_ID + "|@user");
    }

    @Test
    void routesNextToPlayer() {
        StubAudioPlayerPool pool = new StubAudioPlayerPool();
        pool.primeGuild(GUILD_ID);
        MusicPlayerInteractionDispatcher dispatcher = new MusicPlayerInteractionDispatcher(
                new RecordingVoiceConnectionManager(), pool, null, PLAYER_ID);

        dispatcher.handle(new MusicPlayerInteraction.Next(PLAYER_ID, new Request(REQUEST_ID, "@user"), GUILD_ID));

        assertThat(pool.nextCalls).containsExactly(GUILD_ID + "|@user");
    }

    @Test
    void routesPreviousToPlayer() {
        StubAudioPlayerPool pool = new StubAudioPlayerPool();
        pool.primeGuild(GUILD_ID);
        MusicPlayerInteractionDispatcher dispatcher = new MusicPlayerInteractionDispatcher(
                new RecordingVoiceConnectionManager(), pool, null, PLAYER_ID);

        dispatcher.handle(new MusicPlayerInteraction.Previous(PLAYER_ID, new Request(REQUEST_ID, "@user"), GUILD_ID));

        assertThat(pool.previousCalls).containsExactly(GUILD_ID + "|@user");
    }

    @Test
    void routesRewindToPlayer() {
        StubAudioPlayerPool pool = new StubAudioPlayerPool();
        pool.primeGuild(GUILD_ID);
        MusicPlayerInteractionDispatcher dispatcher = new MusicPlayerInteractionDispatcher(
                new RecordingVoiceConnectionManager(), pool, null, PLAYER_ID);

        dispatcher.handle(new MusicPlayerInteraction.Rewind(PLAYER_ID, new Request(REQUEST_ID, "@user"), GUILD_ID, 15000));

        assertThat(pool.rewindCalls).containsExactly("15000|" + GUILD_ID + "|@user");
    }

    @Test
    void routesForwardToPlayer() {
        StubAudioPlayerPool pool = new StubAudioPlayerPool();
        pool.primeGuild(GUILD_ID);
        MusicPlayerInteractionDispatcher dispatcher = new MusicPlayerInteractionDispatcher(
                new RecordingVoiceConnectionManager(), pool, null, PLAYER_ID);

        dispatcher.handle(new MusicPlayerInteraction.Forward(PLAYER_ID, new Request(REQUEST_ID, "@user"), GUILD_ID, 30000));

        assertThat(pool.forwardCalls).containsExactly("30000|" + GUILD_ID + "|@user");
    }

    @Test
    void routesPlaylistToPlayPlaylist() {
        StubAudioPlayerPool pool = new StubAudioPlayerPool();
        pool.useLoader(new AudioTrackLoader() {
            @Override
            public void load(String query, String requesterTag, AudioTrackLoadCallback callback) {
                callback.playlistLoaded(List.of(track("Song A"), track("Song B"), track("Song C")));
            }

            @Override
            public void search(String query, int limit, AudioTrackSearchCallback callback) {
                throw new AssertionError("Search should not be used");
            }
        });
        MusicPlayerInteractionDispatcher dispatcher = new MusicPlayerInteractionDispatcher(
                new RecordingVoiceConnectionManager(), pool, null, PLAYER_ID);

        dispatcher.handle(new MusicPlayerInteraction.Play(PLAYER_ID, REQUEST_ID, GUILD_ID,
                new TrackRequest("https://youtube.com/playlist?list=PLxyz", TEXT_CHANNEL_ID, null)));

        assertThat(pool.playPlaylistCalls).containsExactly(GUILD_ID  + "|3");
    }

    @Test
    void dispatchesSearchInteractionToLoaderAndPublishesSearchResults() {
        StubAudioPlayerPool pool = new StubAudioPlayerPool();
        pool.useLoader(new AudioTrackLoader() {
            @Override
            public void load(String query, String requesterTag, AudioTrackLoadCallback callback) {
                throw new AssertionError("Search should not use load()");
            }

            @Override
            public void search(String query, int limit, AudioTrackSearchCallback callback) {
                pool.searchCalls.add(query + "|" + limit);
                callback.searchResults(List.of(metadata("One More Time"), metadata("Digital Love")));
            }
        });
        RecordingEventDispatcher eventDispatcher = new RecordingEventDispatcher();
        MusicPlayerInteractionDispatcher dispatcher = new MusicPlayerInteractionDispatcher(
                new RecordingVoiceConnectionManager(), pool, eventDispatcher, PLAYER_ID);

        dispatcher.handle(new MusicPlayerInteraction.Search(REQUEST_ID, GUILD_ID, "daft punk", 5));

        assertThat(pool.searchCalls).containsExactly("ytsearch:daft punk|5");
        assertThat(eventDispatcher.events).hasSize(1);
        assertThat(eventDispatcher.events.getFirst()).isInstanceOf(MusicPlayerEvent.SearchResults.class);
        MusicPlayerEvent.SearchResults results = (MusicPlayerEvent.SearchResults) eventDispatcher.events.getFirst();
        assertThat(results.tracks()).extracting(TrackMetadata::title)
                .containsExactly("One More Time", "Digital Love");
    }

    @Test
    void searchResultsDefensivelyCopyIncomingTracks() {
        List<TrackMetadata> tracks = new ArrayList<>();
        tracks.add(metadata("One More Time"));

        MusicPlayerEvent.SearchResults results = new MusicPlayerEvent.SearchResults(PLAYER_ID, REQUEST_ID, GUILD_ID, tracks);
        tracks.add(metadata("Digital Love"));

        assertThat(results.tracks()).extracting(TrackMetadata::title)
                .containsExactly("One More Time");
    }

    @Test
    void dispatchesEmptySearchResultsWhenSearchReturnsNoMatches() {
        StubAudioPlayerPool pool = new StubAudioPlayerPool();
        pool.useLoader(new AudioTrackLoader() {
            @Override
            public void load(String query, String requesterTag, AudioTrackLoadCallback callback) {
                throw new AssertionError("Search should not use load()");
            }

            @Override
            public void search(String query, int limit, AudioTrackSearchCallback callback) {
                pool.searchCalls.add(query + "|" + limit);
                callback.noMatches();
            }
        });
        RecordingEventDispatcher eventDispatcher = new RecordingEventDispatcher();
        MusicPlayerInteractionDispatcher dispatcher = new MusicPlayerInteractionDispatcher(
                new RecordingVoiceConnectionManager(), pool, eventDispatcher, PLAYER_ID);

        dispatcher.handle(new MusicPlayerInteraction.Search(REQUEST_ID, GUILD_ID, "daft punk", 5));

        assertThat(eventDispatcher.events).hasSize(1);
        assertThat(eventDispatcher.events.getFirst()).isInstanceOf(MusicPlayerEvent.SearchResults.class);
        assertThat(((MusicPlayerEvent.SearchResults) eventDispatcher.events.getFirst()).tracks()).isEmpty();
    }

    // --- STUB ---

    private final class StubAudioPlayerPool extends AudioPlayerPool {

        final List<String> loaderCalls = new ArrayList<>();
        final List<String> togglePauseCalls = new ArrayList<>();
        final List<String> nextCalls = new ArrayList<>();
        final List<String> previousCalls = new ArrayList<>();
        final List<String> rewindCalls = new ArrayList<>();
        final List<String> forwardCalls = new ArrayList<>();
        final List<String> playPlaylistCalls = new ArrayList<>();
        final List<String> searchCalls = new ArrayList<>();

        private final Set<String> primedGuilds = new HashSet<>();
        private AudioTrackLoader stubLoader = null;

        StubAudioPlayerPool() {
            super(null, null, null, "test");
        }

        void primeGuild(String guildId) {
            primedGuilds.add(guildId);
        }

        void useLoader(AudioTrackLoader loader) {
            this.stubLoader = loader;
        }

        @Override
        public AudioPlayer getOrCreate(String guildId) {
            primedGuilds.add(guildId);
            return new StubPlayer(guildId);
        }

        @Override
        public AudioTrackLoader getLoader(String guildId) {
            if (stubLoader != null) return stubLoader;
            return new AudioTrackLoader() {
                @Override
                public void load(String query, String requesterTag, AudioTrackLoadCallback callback) {
                    loaderCalls.add(query);
                }

                @Override
                public void search(String query, int limit, AudioTrackSearchCallback callback) {
                    searchCalls.add(query + "|" + limit);
                }
            };
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
                super(new NoOpEngine(), new NoOpAudioProvider(), null, PROPS, null, "test", guildId, () -> {});
                this.guildId = guildId;
            }

            @Override
            public void togglePause(Request request) {
                togglePauseCalls.add(guildId + "|" + request.requesterTag());
            }

            @Override
            public void next(Request request) {
                nextCalls.add(guildId + "|" + request.requesterTag());
            }

            @Override
            public void previous(Request request) {
                previousCalls.add(guildId + "|" + request.requesterTag());
            }

            @Override
            public void rewind(Duration seek, Request request) {
                rewindCalls.add(seek.toMillis() + "|" + guildId + "|" + request.requesterTag());
            }

            @Override
            public void forward(Duration seek, Request request) {
                forwardCalls.add(seek.toMillis() + "|" + guildId + "|" + request.requesterTag());
            }

            @Override
            public void clear(Request request) {
            }

            @Override
            public void play(List<Track> tracks) {
                playPlaylistCalls.add(guildId + "|" + tracks.size());
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

    private static final class RecordingEventDispatcher implements MusicPlayerEventDispatcher {

        private final List<MusicPlayerEvent> events = new ArrayList<>();

        @Override
        public void dispatch(MusicPlayerEvent event) {
            events.add(event);
        }
    }

    private static Track track(String title) {
        return new Track() {
            @Override public String title() { return title; }
            @Override public String author() { return "Author"; }
            @Override public String uri() { return "https://example.com/" + title; }
            @Override public String requesterTag() { return null; }
            @Override public long duration() { return 120_000; }
            @Override public long position() { return 0; }
            @Override public void setPosition(long positionMs) {}
            @Override public Track clone() { return this; }
        };
    }

    private static TrackMetadata metadata(String title) {
        return new TrackMetadata(title, "Author", "https://example.com/" + title, 120_000);
    }
}
