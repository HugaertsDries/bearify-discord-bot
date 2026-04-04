package com.bearify.music.player.agent.lava;

import com.bearify.music.player.agent.domain.AudioTrackLoader;
import com.bearify.music.player.bridge.model.TrackMetadata;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LavaAudioTrackLoaderTest {

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
