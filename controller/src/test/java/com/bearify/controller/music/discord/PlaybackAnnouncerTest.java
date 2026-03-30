package com.bearify.controller.music.discord;

import com.bearify.discord.api.message.ComponentMessage;
import com.bearify.discord.api.message.Container;
import com.bearify.discord.api.message.ContainerChild;
import com.bearify.discord.api.message.TextBlock;
import com.bearify.music.player.bridge.model.TrackMetadata;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybackAnnouncerTest {

    @Test
    void renderUsesConfiguredPrimaryColorForDefaultNotification() {
        PlaybackAnnouncer announcer = new PlaybackAnnouncer(new AnnouncerProperties("#123456", "#AA0000", "Footer", java.time.Duration.ofSeconds(15)));

        ComponentMessage message = announcer.render(baseState().notification("Skipped by @user").build());

        assertThat(message.containers()).hasSize(4);
        assertThat(message.containers().getFirst().accentColor()).isEqualTo(0x123456);
        assertThat(texts(message)).anyMatch(text -> text.contains("Skipped by @user"));
    }

    @Test
    void renderUsesConfiguredErrorColorForErrorNotification() {
        PlaybackAnnouncer announcer = new PlaybackAnnouncer(new AnnouncerProperties("#123456", "#AA0000", "Footer", java.time.Duration.ofSeconds(15)));

        ComponentMessage message = announcer.render(baseState()
                .notification(new PlaybackAnnouncerState.Notification(
                        PlaybackAnnouncerState.NotificationStyle.ERROR,
                        "Something went wrong"))
                .build());

        assertThat(message.containers()).hasSize(4);
        assertThat(message.containers().getFirst().accentColor()).isEqualTo(0xAA0000);
        assertThat(texts(message)).anyMatch(text -> text.contains("Something went wrong"));
    }

    private static PlaybackAnnouncerState.Builder baseState() {
        return PlaybackAnnouncerState.builder()
                .playbackState(PlaybackAnnouncerState.PlaybackState.PLAYING)
                .track(new TrackMetadata("Song", "Artist", "https://example.com", 60_000))
                .requesterTag("@user")
                .upNext(List.of(new TrackMetadata("Next", "Artist 2", "https://example.com/next", 120_000)))
                .artworkUri(URI.create("https://cdn.example/art.png"))
                .footer("Footer")
                .paused(false);
    }

    private static List<String> texts(ComponentMessage message) {
        return message.containers().stream()
                .flatMap(container -> container.children().stream())
                .filter(TextBlock.class::isInstance)
                .map(TextBlock.class::cast)
                .map(TextBlock::text)
                .toList();
    }
}
