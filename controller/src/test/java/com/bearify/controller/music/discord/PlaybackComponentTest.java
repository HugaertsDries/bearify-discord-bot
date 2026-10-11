package com.bearify.controller.music.discord;

import com.bearify.controller.dev.PlaybackAnnouncerPreset;
import com.bearify.controller.dev.PlaybackAnnouncerPresets;
import com.bearify.discord.api.message.ActionRow;
import com.bearify.discord.api.message.ButtonStyle;
import com.bearify.discord.api.message.InteractiveButton;
import com.bearify.discord.api.message.ComponentMessage;
import com.bearify.discord.api.message.Container;
import com.bearify.discord.api.message.ContainerChild;
import com.bearify.discord.api.message.Divider;
import com.bearify.discord.api.message.ImageBlock;
import com.bearify.discord.api.message.Section;
import com.bearify.discord.api.message.Spacer;
import com.bearify.discord.api.message.TextBlock;
import com.bearify.music.player.bridge.model.TrackMetadata;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybackComponentTest {

    @Test
    void renderUsesConfiguredPrimaryColorForDefaultNotification() {
        PlaybackComponent announcer = new PlaybackComponent(new AnnouncerProperties("#123456", "#AA0000", "Footer", java.time.Duration.ofSeconds(15)));

        ComponentMessage message = announcer.render(baseState().notification("Skipped by @user").build());

        assertThat(message.containers()).hasSize(4);
        assertThat(message.containers().getFirst().accentColor()).isEqualTo(0x123456);
        assertThat(texts(message)).anyMatch(text -> text.contains("Skipped by @user"));
    }

    @Test
    void renderUsesConfiguredErrorColorForErrorNotification() {
        PlaybackComponent announcer = new PlaybackComponent(new AnnouncerProperties("#123456", "#AA0000", "Footer", java.time.Duration.ofSeconds(15)));

        ComponentMessage message = announcer.render(baseState()
                .notification(new PlaybackComponentState.Notification(
                        PlaybackComponentState.NotificationStyle.ERROR,
                        "Something went wrong"))
                .build());

        assertThat(message.containers()).hasSize(4);
        assertThat(message.containers().getFirst().accentColor()).isEqualTo(0xAA0000);
        assertThat(texts(message)).anyMatch(text -> text.contains("Something went wrong"));
    }

    @Test
    void renderOmitsFooterWhenStateHasNone() {
        PlaybackComponent announcer = new PlaybackComponent(new AnnouncerProperties("#123456", "#AA0000", "Footer", java.time.Duration.ofSeconds(15)));

        ComponentMessage message = announcer.render(baseState().footer(null).build());

        assertThat(message.items()).last().isInstanceOf(Container.class);
    }

    @Test
    void rendersNoMoreLineWhenExactlyThreeAreQueued() {
        PlaybackComponent announcer = new PlaybackComponent(new AnnouncerProperties("#123456", "#AA0000", "Footer", java.time.Duration.ofSeconds(15)));

        ComponentMessage message = announcer.render(new PlaybackAnnouncerPresets("Footer").get(PlaybackAnnouncerPreset.BROADCAST));

        Container upNext = message.containers().getLast();
        assertThat(upNext.children().getLast()).isInstanceOf(TextBlock.class);
        assertThat(upNext.children()).noneMatch(child -> child instanceof ActionRow);
        assertThat(texts(message)).noneMatch(text -> text.contains("more on the trail"));
    }

    @Test
    void rendersMoreLineWithShowButtonWhenMoreThanThreeAreQueued() {
        PlaybackComponent announcer = new PlaybackComponent(new AnnouncerProperties("#123456", "#AA0000", "Footer", java.time.Duration.ofSeconds(15)));

        ComponentMessage message = announcer.render(new PlaybackAnnouncerPresets("Footer").get(PlaybackAnnouncerPreset.HEAVY_QUEUE));

        Container upNext = message.containers().getLast();
        assertThat(upNext.children().get(upNext.children().size() - 3)).isInstanceOf(Divider.class);
        assertThat(upNext.children().get(upNext.children().size() - 2)).isInstanceOf(Spacer.class);
        assertThat(upNext.children().getLast()).isInstanceOfSatisfying(Section.class, section -> {
            assertThat(section.texts()).containsExactly("-# +9 more on the trail · 35m 22s");
            assertThat(section.accessory()).hasValueSatisfying(accessory -> assertThat(accessory)
                    .isEqualTo(new InteractiveButton(ButtonStyle.SECONDARY, "player:queue", "Show")));
        });
    }

    @Test
    void stretchesUpNextRightUnderItsHeader() {
        PlaybackComponent announcer = new PlaybackComponent(new AnnouncerProperties("#123456", "#AA0000", "Footer", java.time.Duration.ofSeconds(15)));

        ComponentMessage message = announcer.render(new PlaybackAnnouncerPresets("Footer").get(PlaybackAnnouncerPreset.BROADCAST));

        Container upNext = message.containers().getLast();
        assertThat(upNext.children().getFirst()).isInstanceOf(Section.class);
        assertThat(upNext.children().get(1)).isInstanceOf(Spacer.class);
    }

    @Test
    void leavesLengthOffMoreLineWhenBearSentNoQueueLength() {
        PlaybackComponent announcer = new PlaybackComponent(new AnnouncerProperties("#123456", "#AA0000", "Footer", java.time.Duration.ofSeconds(15)));

        ComponentMessage message = announcer.render(baseState().queueSize(5).build());

        assertThat(message.containers().getLast().children().getLast()).isInstanceOfSatisfying(Section.class, section ->
                assertThat(section.texts()).containsExactly("-# +4 more on the trail"));
    }

    @Test
    void showsLiveInsteadOfLengthForStreams() {
        PlaybackComponent announcer = new PlaybackComponent(new AnnouncerProperties("#123456", "#AA0000", "Footer", java.time.Duration.ofSeconds(15)));

        ComponentMessage message = announcer.render(baseState()
                .upNext(List.of(new TrackMetadata("Radio", "Station", "https://example.com/radio", Long.MAX_VALUE)))
                .build());

        assertThat(texts(message)).anyMatch(text -> text.contains("Station • 🔴 LIVE"));
    }

    @Test
    void rendersLiveBadgeInsteadOfDurationForPlayingStream() {
        PlaybackComponent announcer = new PlaybackComponent(new AnnouncerProperties("#123456", "#AA0000", "Footer", java.time.Duration.ofSeconds(15)));

        ComponentMessage message = announcer.render(baseState()
                .track(new TrackMetadata("Radio", "Station", "https://example.com/radio", Long.MAX_VALUE))
                .build());

        assertThat(texts(message)).contains("### Station • 🔴 LIVE");
    }

    @Test
    void staysUnderFortyComponentsWithHeavyQueue() {
        PlaybackComponent announcer = new PlaybackComponent(new AnnouncerProperties("#123456", "#AA0000", "Footer", java.time.Duration.ofSeconds(15)));

        PlaybackAnnouncerPresets presets = new PlaybackAnnouncerPresets("Footer");

        for (PlaybackAnnouncerPreset preset : PlaybackAnnouncerPreset.values()) {
            assertThat(componentCount(announcer.render(presets.get(preset)))).as(preset.name()).isLessThan(40);
        }
    }

    @Test
    void truncatesBeforeAnEmojiInsteadOfSplittingIt() {
        String title = "a".repeat(8) + "🎵" + "tail";

        assertThat(PlaybackComponent.truncate(title, 10)).isEqualTo("a".repeat(8) + "…");
    }

    // Discord counts every component, nested ones included, against its 40-per-message limit
    private static int componentCount(ComponentMessage message) {
        return message.items().stream().mapToInt(item -> switch (item) {
            case Container container -> 1 + container.children().stream().mapToInt(PlaybackComponentTest::componentCount).sum();
            case TextBlock ignored -> 1;
            case ImageBlock ignored -> 1;
        }).sum();
    }

    private static int componentCount(ContainerChild child) {
        return switch (child) {
            case ActionRow row -> 1 + row.buttons().size();
            case Section section -> 1 + section.texts().size() + section.accessory().map(accessory -> 1).orElse(0);
            case TextBlock ignored -> 1;
            case Divider ignored -> 1;
            case ImageBlock ignored -> 1;
            case Spacer ignored -> 1;
        };
    }

    private static PlaybackComponentState.Builder baseState() {
        return PlaybackComponentState.builder()
                .playbackState(PlaybackComponentState.PlaybackState.PLAYING)
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
