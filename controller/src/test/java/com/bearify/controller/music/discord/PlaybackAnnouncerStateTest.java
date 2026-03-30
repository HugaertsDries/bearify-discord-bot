package com.bearify.controller.music.discord;

import com.bearify.music.player.bridge.model.TrackMetadata;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybackAnnouncerStateTest {

    @Test
    void builderExposesRichTrackStateWithoutOptionalParameters() {
        TrackMetadata currentTrack = new TrackMetadata("Song", "Artist", "https://example.com", 60_000, "https://cdn.example/art.png");
        TrackMetadata nextTrack = new TrackMetadata("Next", "Artist 2", "https://example.com/next", 120_000);
        PlaybackAnnouncerState state = PlaybackAnnouncerState.builder()
                .playbackState(PlaybackAnnouncerState.PlaybackState.PAUSED)
                .notification("Skipped by @user")
                .track(currentTrack)
                .requesterTag("@user")
                .upNext(List.of(nextTrack))
                .artworkUri(URI.create("https://cdn.example/art.png"))
                .footer("Footer")
                .paused(true)
                .build();

        assertThat(state.playbackState()).isEqualTo(PlaybackAnnouncerState.PlaybackState.PAUSED);
        assertThat(state.notification()).contains(new PlaybackAnnouncerState.Notification(
                PlaybackAnnouncerState.NotificationStyle.INFO,
                "Skipped by @user"));
        assertThat(state.track()).isEqualTo(currentTrack);
        assertThat(state.upNext()).containsExactly(nextTrack);
        assertThat(state.artworkUri()).contains(URI.create("https://cdn.example/art.png"));
        assertThat(state.footerText()).isEqualTo("Footer");
        assertThat(state.paused()).isTrue();
    }

    @Test
    void builderDefaultsMissingOptionalValuesToEmpty() {
        TrackMetadata currentTrack = new TrackMetadata("Song", "Artist", "https://example.com", 60_000);
        PlaybackAnnouncerState state = PlaybackAnnouncerState.builder()
                .playbackState(PlaybackAnnouncerState.PlaybackState.PLAYING)
                .track(currentTrack)
                .requesterTag("@user")
                .build();

        assertThat(state.notification()).isEmpty();
        assertThat(state.artworkUri()).isEmpty();
        assertThat(state.upNext()).isEmpty();
        assertThat(state.footerText()).isEqualTo("Bearify • Powered by Bearable Software");
        assertThat(state.paused()).isFalse();
    }
}
