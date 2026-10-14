package com.bearify.music.player.agent.lava;

import com.bearify.music.player.agent.domain.AudioTrackLoader;
import com.bearify.music.player.bridge.model.TrackMetadata;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LavaAudioTrackLoaderTest {

    @Test
    void rejectsLinksOutsideTheSourcesHostsWithoutFetchingThem() {
        AudioPlayerManager playerManager = mock(AudioPlayerManager.class);
        AtomicBoolean noMatches = new AtomicBoolean();

        new LavaAudioTrackLoader(playerManager, 3).load("http://redis:6379/#.bandcamp.com/track/x", "@user", new AudioTrackLoader.AudioTrackLoadCallback() {
            @Override public void trackLoaded(com.bearify.music.player.agent.domain.Track track) {}
            @Override public void playlistLoaded(List<com.bearify.music.player.agent.domain.Track> tracks) {}
            @Override public void noMatches() { noMatches.set(true); }
            @Override public void loadFailed(String message) {}
        });

        verify(playerManager, never()).loadItem(anyString(), any(AudioLoadResultHandler.class));
        assertThat(noMatches).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://www.youtube.com/watch?v=abc", "ytsearch:lofi beats https://example.com"})
    void loadsSearchesAndLinksToTheSourcesHosts(String query) {
        AudioPlayerManager playerManager = mock(AudioPlayerManager.class);

        new LavaAudioTrackLoader(playerManager, 3).load(query, "@user", new AudioTrackLoader.AudioTrackLoadCallback() {
            @Override public void trackLoaded(com.bearify.music.player.agent.domain.Track track) {}
            @Override public void playlistLoaded(List<com.bearify.music.player.agent.domain.Track> tracks) {}
            @Override public void noMatches() {}
            @Override public void loadFailed(String message) {}
        });

        verify(playerManager).loadItem(eq(query), any(AudioLoadResultHandler.class));
    }

    @Test
    void rejectsSearchingAnUntrustedLink() {
        AudioPlayerManager playerManager = mock(AudioPlayerManager.class);
        AtomicBoolean noMatches = new AtomicBoolean();

        new LavaAudioTrackLoader(playerManager, 3).search("http://redis:6379/#.bandcamp.com/track/x", 5, new AudioTrackLoader.AudioTrackSearchCallback() {
            @Override public void searchResults(List<TrackMetadata> tracks) {}
            @Override public void noMatches() { noMatches.set(true); }
            @Override public void loadFailed(String message) {}
        });

        verify(playerManager, never()).loadItem(anyString(), any(AudioLoadResultHandler.class));
        assertThat(noMatches).isTrue();
    }

    @Test
    void searchReturnsTrackMetadataForSearchResults() {
        AudioPlayerManager playerManager = mock(AudioPlayerManager.class);
        LavaAudioTrackLoader loader = new LavaAudioTrackLoader(playerManager, 3);
        AtomicReference<List<TrackMetadata>> results = new AtomicReference<>();

        doAnswer(invocation -> {
            AudioLoadResultHandler handler = invocation.getArgument(1);
            handler.playlistLoaded(searchResultPlaylist(
                    track("One More Time", "Daft Punk", "https://example.com/1", 320_000),
                    track("Digital Love", "Daft Punk", "https://example.com/2", 300_000),
                    track("Voyager", "Daft Punk", "https://example.com/3", 285_000),
                    track("Harder Better Faster Stronger", "Daft Punk", "https://example.com/4", 224_000)
            ));
            return null;
        }).when(playerManager).loadItem(anyString(), any());

        loader.search("ytsearch:daft punk", 3, new AudioTrackLoader.AudioTrackSearchCallback() {
            @Override
            public void searchResults(List<TrackMetadata> tracks) {
                results.set(tracks);
            }

            @Override
            public void noMatches() {
                throw new AssertionError("Expected search results");
            }

            @Override
            public void loadFailed(String message) {
                throw new AssertionError("Expected search results");
            }
        });

        assertThat(results).hasValueSatisfying(tracks ->
                assertThat(tracks).extracting(TrackMetadata::title)
                        .containsExactly("One More Time", "Digital Love", "Voyager"));
    }

    private static AudioPlaylist searchResultPlaylist(AudioTrack... tracks) {
        AudioPlaylist playlist = mock(AudioPlaylist.class);
        when(playlist.isSearchResult()).thenReturn(true);
        when(playlist.getTracks()).thenReturn(List.of(tracks));
        return playlist;
    }

    private static AudioTrack track(String title, String author, String uri, long durationMs) {
        AudioTrack track = mock(AudioTrack.class);
        when(track.getInfo()).thenReturn(new AudioTrackInfo(
                title,
                author,
                durationMs,
                "id-" + title,
                false,
                uri,
                null,
                null));
        when(track.getDuration()).thenReturn(durationMs);
        return track;
    }
}
