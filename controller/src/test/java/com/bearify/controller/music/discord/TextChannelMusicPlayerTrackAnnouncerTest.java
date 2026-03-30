package com.bearify.controller.music.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.controller.music.domain.MusicPlayerTrackAnnouncer;
import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.Guild;
import com.bearify.discord.api.gateway.SentMessage;
import com.bearify.discord.api.gateway.TextChannel;
import com.bearify.discord.api.message.ActionRow;
import com.bearify.discord.api.message.ComponentMessage;
import com.bearify.discord.api.message.Container;
import com.bearify.discord.api.message.ContainerChild;
import com.bearify.discord.api.message.InteractiveButton;
import com.bearify.discord.api.message.Section;
import com.bearify.discord.api.message.TextBlock;
import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.discord.api.voice.VoiceSessionListener;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.Request;
import com.bearify.music.player.bridge.model.TrackMetadata;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TextChannelMusicPlayerTrackAnnouncerTest {

    @Test
    void defaultFooterStartsWithBearEmoji() {
        AnnouncerProperties props = new AnnouncerProperties("#FFA500", "#FF4444",
                "\uD83D\uDC3B Bearify \u2022 Powered by Bearable Software",
                Duration.ofSeconds(15));
        assertThat(props.footer()).startsWith(BearifyEmoji.BEAR + " Bearify");
    }

    @Test
    void acceptPostsComponentMessageWhenTrackStartsAndNoMessageExists() {
        AtomicReference<ComponentMessage> sent = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(sent, new AtomicReference<>(), new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));

        assertThat(sent.get()).isNotNull();
        assertThat(sent.get().label()).isEqualTo("playback-announcer");
        assertThat(sent.get().containers()).hasSize(2);
        assertThat(allTexts(sent.get())).anyMatch(text -> text.contains("Song"));
        assertThat(buttonLabels(sent.get())).containsExactly("❙◀︎◀︎", "◀︎", "❚❚", "▶︎", "▶︎▶︎❙");
    }

    @Test
    void acceptShowsPausedStateAfterPausedEvent() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Paused("player-1", new Request("req-2", "@user"), "guild-1"));

        assertThat(updated.get()).isNotNull();
        assertThat(allTexts(updated.get())).anyMatch(text -> text.contains("\u26AA ON THE AIR"));
        assertThat(buttonLabels(updated.get())).containsExactly("❙◀︎◀︎", "◀︎", "▶︎", "▶︎", "▶︎▶︎❙");
    }

    @Test
    void acceptRestoresPlayingCopyAfterResume() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Paused("player-1", new Request("req-2", "@user"), "guild-1"));
        announcer.accept(new MusicPlayerEvent.Resumed("player-1", new Request("req-3", "@user"), "guild-1"));

        assertThat(updated.get()).isNotNull();
        assertThat(allTexts(updated.get())).anyMatch(text -> text.contains("\uD83D\uDD34 ON THE AIR"));
        assertThat(buttonLabels(updated.get())).containsExactly("❙◀︎◀︎", "◀︎", "❚❚", "▶︎", "▶︎▶︎❙");
    }

    @Test
    void acceptShowsTemporarySkippedAction() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Skipped("player-1", new Request("req-2", "@user"), "guild-1"));

        assertThat(allTexts(updated.get())).anyMatch(text -> text.contains("Last track skipped by @user"));
    }

    @Test
    void acceptShowsTemporaryWentBackAction() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.WentBack("player-1", new Request("req-2", "@user"), "guild-1"));

        assertThat(allTexts(updated.get())).anyMatch(text -> text.contains("Jumped back by @user"));
    }

    @Test
    void acceptReplacesOlderTemporaryActionWithNewerOne() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Skipped("player-1", new Request("req-2", "@user"), "guild-1"));
        announcer.accept(new MusicPlayerEvent.Cleared("player-1", new Request("req-3", "@other"), "guild-1", List.of()));

        assertThat(allTexts(updated.get())).anyMatch(text -> text.contains("Cleared by @other"));
        assertThat(allTexts(updated.get())).noneMatch(text -> text.contains("Last track skipped"));
    }

    @Test
    void acceptClearsTemporaryActionAfterTimeout() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofMillis(50));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Forwarded("player-1", new Request("req-2", "@user"), "guild-1", 30_000));

        Awaitility.await().atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                assertThat(updated.get()).isNotNull());
        Awaitility.await().atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                assertThat(allTexts(updated.get())).noneMatch(text -> text.contains("Forwarded by @user")));
    }

    @Test
    void acceptUpdatesExistingMessageWhenTrackErrorArrives() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        AtomicInteger sends = new AtomicInteger();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, sends, Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(trackError("player-1"));

        assertThat(sends.get()).isEqualTo(1);
        assertThat(allTexts(updated.get())).anyMatch(text -> text.contains("Something went wrong loading this track"));
    }

    @Test
    void acceptDeletesExistingMessageOnStopped() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        AtomicInteger sends = new AtomicInteger();
        AtomicInteger deletes = new AtomicInteger();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, sends, deletes, Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Stopped("player-1", "req-2", "guild-1"));

        assertThat(sends.get()).isEqualTo(1);
        assertThat(deletes.get()).isEqualTo(1);
    }

    @Test
    void acceptDoesNotDeleteMessageForUnrelatedNonTerminalEvent() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        AtomicInteger sends = new AtomicInteger();
        AtomicInteger deletes = new AtomicInteger();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, sends, deletes, Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.NothingToGoBack("player-1", "req-2", "guild-1"));

        assertThat(sends.get()).isEqualTo(1);
        assertThat(deletes.get()).isZero();
    }

    @Test
    void includesUpNextFromTrackStart() {
        AtomicReference<ComponentMessage> sent = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(sent, new AtomicReference<>(), new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStartWithUpNext("player-1"));

        assertThat(allTexts(sent.get())).anyMatch(text -> text.contains("**Up Next**"));
        assertThat(allTexts(sent.get())).anyMatch(text -> text.contains("1. Hotel California"));
        assertThat(allTexts(sent.get())).anyMatch(text -> text.contains("2. Stairway to Heaven"));
    }

    @Test
    void omitsUpNextWhenQueueIsEmpty() {
        AtomicReference<ComponentMessage> sent = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(sent, new AtomicReference<>(), new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));

        assertThat(allTexts(sent.get())).noneMatch(text -> text.contains("**Up Next**"));
    }

    @Test
    void upNextUpdatesWhenQueueUpdatedEventArrives() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.QueueUpdated("player-1", "req-2", "guild-1",
                List.of(new TrackMetadata("New Song", "New Artist", "https://example.com/new", 120_000))));

        assertThat(allTexts(updated.get())).anyMatch(text -> text.contains("1. New Song"));
    }

    @Test
    void clearedEventRemovesUpNextWhenQueueBecomesEmpty() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStartWithUpNext("player-1"));
        announcer.accept(new MusicPlayerEvent.Cleared("player-1", new Request("req-2", "@user"), "guild-1", List.of()));

        assertThat(allTexts(updated.get())).noneMatch(text -> text.contains("**Up Next**"));
        assertThat(allTexts(updated.get())).anyMatch(text -> text.contains("Cleared by @user"));
    }

    @Test
    void truncatesLongTrackTitleAt45Characters() {
        AtomicReference<ComponentMessage> sent = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(sent, new AtomicReference<>(), new AtomicInteger(), Duration.ofSeconds(15));

        String longTitle = "A".repeat(100);
        announcer.accept(new MusicPlayerEvent.TrackStart("player-1", new Request("req-1", "@user"), "guild-1",
                new TrackMetadata(longTitle, "Artist", "https://example.com", 60_000), List.of()));

        assertThat(allTexts(sent.get())).anyMatch(text -> text.contains("A".repeat(44) + "…"));
    }

    @Test
    void truncatesLongAuthorAt40Characters() {
        AtomicReference<ComponentMessage> sent = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(sent, new AtomicReference<>(), new AtomicInteger(), Duration.ofSeconds(15));

        String longAuthor = "B".repeat(60);
        announcer.accept(new MusicPlayerEvent.TrackStart("player-1", new Request("req-1", "@user"), "guild-1",
                new TrackMetadata("Song", longAuthor, "https://example.com", 60_000), List.of()));

        assertThat(allTexts(sent.get())).anyMatch(text -> text.contains("B".repeat(39) + "…"));
    }

    @Test
    void usesTrackArtworkUrlBeforeYoutubeFallback() {
        TrackMetadata track = new TrackMetadata(
                "Song",
                "Artist",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                60_000,
                "https://cdn.example/art.png"
        );

        assertThat(new YoutubeThumbnailResolver().resolve(track)).contains(URI.create("https://cdn.example/art.png"));
    }

    @Test
    void derivesYoutubeArtworkWhenMetadataArtworkIsMissing() {
        TrackMetadata track = new TrackMetadata(
                "Song",
                "Artist",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                60_000,
                null
        );

        assertThat(new YoutubeThumbnailResolver().resolve(track))
                .contains(URI.create("https://img.youtube.com/vi/dQw4w9WgXcQ/maxresdefault.jpg"));
    }

    private static MusicPlayerTrackAnnouncer announcer(AtomicReference<ComponentMessage> sent,
                                                       AtomicReference<ComponentMessage> updated,
                                                       AtomicInteger sends,
                                                       Duration timeout) {
        return announcer(sent, updated, sends, new AtomicInteger(), timeout);
    }

    private static MusicPlayerTrackAnnouncer announcer(AtomicReference<ComponentMessage> sent,
                                                       AtomicReference<ComponentMessage> updated,
                                                       AtomicInteger sends,
                                                       AtomicInteger deletes,
                                                       Duration timeout) {
        return new TextChannelMusicPlayerTrackAnnouncer(
                discordClient(sent, sends, updated, deletes),
                new AnnouncerProperties("#FFA500", "#FF4444", "Bearify", timeout),
                "text-1");
    }

    private static MusicPlayerEvent.TrackStart trackStart(String playerId) {
        return new MusicPlayerEvent.TrackStart(playerId, new Request("req-1", "@user"), "guild-1",
                new TrackMetadata("Song", "Artist", "https://example.com", 60_000), List.of());
    }

    private static MusicPlayerEvent.TrackStart trackStartWithUpNext(String playerId) {
        return new MusicPlayerEvent.TrackStart(playerId, new Request("req-1", "@user"), "guild-1",
                new TrackMetadata("Song", "Artist", "https://example.com", 60_000),
                List.of(
                        new TrackMetadata("Hotel California", "Eagles", "https://example.com/1", 390_000),
                        new TrackMetadata("Stairway to Heaven", "Led Zeppelin", "https://example.com/2", 482_000)
                ));
    }

    private static MusicPlayerEvent.TrackError trackError(String playerId) {
        return new MusicPlayerEvent.TrackError(playerId, "req-2", "guild-1",
                new TrackMetadata("Song", "Artist", "https://example.com", 60_000));
    }

    private static DiscordClient discordClient(AtomicReference<ComponentMessage> sent,
                                               AtomicInteger sends,
                                               AtomicReference<ComponentMessage> updated,
                                               AtomicInteger deletes) {
        return new DiscordClient() {
            @Override
            public void start(String token) {
            }

            @Override
            public void start(String token, String guildId) {
            }

            @Override
            public Guild guild(String guildId) {
                return new Guild() {
                    @Override
                    public Optional<VoiceSession> voice() {
                        return Optional.empty();
                    }

                    @Override
                    public void join(String channelId, AudioProvider provider, VoiceSessionListener onJoined) {
                    }
                };
            }

            @Override
            public TextChannel textChannel(String channelId) {
                return new TextChannel() {
                    @Override
                    public void send(String message) {
                    }

                    @Override
                    public SentMessage send(ComponentMessage message) {
                        sent.set(message);
                        sends.incrementAndGet();
                        return new SentMessage() {
                            @Override
                            public void delete() {
                                deletes.incrementAndGet();
                            }

                            @Override
                            public void update(ComponentMessage updatedMessage) {
                                updated.set(updatedMessage);
                            }
                        };
                    }
                };
            }

            @Override
            public void shutdown() {
            }
        };
    }

    private static List<String> allTexts(ComponentMessage message) {
        List<String> texts = new ArrayList<>();
        for (Container container : message.containers()) {
            for (ContainerChild child : container.children()) {
                switch (child) {
                    case TextBlock textBlock -> texts.add(textBlock.text());
                    case Section section -> texts.addAll(section.texts());
                    default -> {
                    }
                }
            }
        }
        return texts;
    }

    private static List<String> buttonLabels(ComponentMessage message) {
        for (Container container : message.containers()) {
            for (ContainerChild child : container.children()) {
                if (child instanceof ActionRow row) {
                    return row.buttons().stream().map(InteractiveButton::label).toList();
                }
            }
        }
        return List.of();
    }
}
