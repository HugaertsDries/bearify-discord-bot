package com.bearify.controller.music.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.controller.music.domain.MusicPlayerTrackAnnouncer;
import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.EmbedMessage;
import com.bearify.discord.api.gateway.Guild;
import com.bearify.discord.api.gateway.SentMessage;
import com.bearify.discord.api.gateway.TextChannel;
import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.discord.api.voice.VoiceSessionListener;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.Request;
import com.bearify.music.player.bridge.model.TrackMetadata;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TextChannelMusicPlayerTrackAnnouncerTest {

    private static final String PLAYING_AUTHOR_TEXT = BearifyEmoji.RED + " ON THE AIR";
    private static final String PAUSED_AUTHOR_TEXT  = "\u26AA ON THE AIR";

    @Test
    void defaultFooterStartsWithBearEmoji() {
        AnnouncerProperties props = new AnnouncerProperties("#FFA500", "#FF4444",
                "\uD83D\uDC3B Bearify \u2022 Powered by Bearable Software",
                Duration.ofSeconds(15));
        assertThat(props.footer()).startsWith(BearifyEmoji.BEAR + " Bearify");
    }

    @Test
    void acceptPostsEmbedWhenTrackStartsAndNoMessageExists() {
        AtomicReference<EmbedMessage> sent = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(sent, new AtomicReference<>(), new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));

        assertThat(sent.get()).isNotNull();
        assertThat(sent.get().authorText()).hasValue(PLAYING_AUTHOR_TEXT);
        assertThat(sent.get().description()).isEmpty();
        assertThat(sent.get().imageFilename()).hasValue("vibing.gif");
    }

    @Test
    void acceptShowsPausedStateAfterPausedEvent() {
        AtomicReference<EmbedMessage> updated = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Paused("player-1", new Request("req-2", "@user"), "guild-1"));

        assertThat(updated.get()).isNotNull();
        assertThat(updated.get().authorText()).hasValue(PAUSED_AUTHOR_TEXT);
        assertThat(updated.get().imageFilename()).hasValue("spacer.png");
    }

    @Test
    void acceptRestoresPlayingCopyAfterResume() {
        AtomicReference<EmbedMessage> updated = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Paused("player-1", new Request("req-2", "@user"), "guild-1"));
        announcer.accept(new MusicPlayerEvent.Resumed("player-1", new Request("req-3", "@user"), "guild-1"));

        assertThat(updated.get()).isNotNull();
        assertThat(updated.get().authorText()).hasValue(PLAYING_AUTHOR_TEXT);
        assertThat(updated.get().imageFilename()).hasValue("vibing.gif");
    }

    @Test
    void acceptShowsTemporarySkippedAction() {
        AtomicReference<EmbedMessage> updated = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Skipped("player-1", new Request("req-2", "@user"), "guild-1"));

        assertThat(updated.get()).isNotNull();
        assertThat(updated.get().description()).hasValue("*Last track skipped by @user*");
    }

    @Test
    void acceptShowsTemporaryWentBackAction() {
        AtomicReference<EmbedMessage> updated = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.WentBack("player-1", new Request("req-2", "@user"), "guild-1"));

        assertThat(updated.get()).isNotNull();
        assertThat(updated.get().description()).hasValue("*Jumped back by @user*");
    }

    @Test
    void acceptReplacesOlderTemporaryActionWithNewerOne() {
        AtomicReference<EmbedMessage> updated = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Skipped("player-1", new Request("req-2", "@user"), "guild-1"));
        announcer.accept(new MusicPlayerEvent.Cleared("player-1", new Request("req-3", "@other"), "guild-1", List.of()));

        assertThat(updated.get()).isNotNull();
        assertThat(updated.get().description()).hasValue("*Cleared by @other*");
    }

    @Test
    void acceptClearsTemporaryActionAfterTimeout() {
        AtomicReference<EmbedMessage> updated = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofMillis(50));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Forwarded("player-1", new Request("req-2", "@user"), "guild-1", 30_000));

        Awaitility.await().atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                assertThat(updated.get()).isNotNull());
        Awaitility.await().atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                assertThat(updated.get().description()).isEmpty());
    }

    @Test
    void acceptUpdatesExistingMessageWhenTrackErrorArrives() {
        AtomicReference<EmbedMessage> updated = new AtomicReference<>();
        AtomicInteger sends = new AtomicInteger();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, sends, Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(trackError("player-1"));

        assertThat(sends.get()).isEqualTo(1);
        assertThat(updated.get()).isNotNull();
    }

    @Test
    void acceptDeletesExistingMessageOnStopped() {
        AtomicReference<EmbedMessage> updated = new AtomicReference<>();
        AtomicInteger sends = new AtomicInteger();
        AtomicInteger deletes = new AtomicInteger();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, sends, deletes, Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Stopped("player-1", "req-2", "guild-1"));

        assertThat(sends.get()).isEqualTo(1);
        assertThat(deletes.get()).isEqualTo(1);
    }

    @Test
    void acceptDoesNotDeleteEmbedForUnrelatedNonTerminalEvent() {
        AtomicReference<EmbedMessage> updated = new AtomicReference<>();
        AtomicInteger sends = new AtomicInteger();
        AtomicInteger deletes = new AtomicInteger();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, sends, deletes, Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.NothingToGoBack("player-1", "req-2", "guild-1"));

        assertThat(sends.get()).isEqualTo(1);
        assertThat(deletes.get()).isZero();
    }

    @Test
    void embedIncludesUpNextFieldWithTracksFromTrackStart() {
        AtomicReference<EmbedMessage> sent = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(sent, new AtomicReference<>(), new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStartWithUpNext("player-1"));

        assertThat(sent.get()).isNotNull();
        assertThat(sent.get().fields()).anySatisfy(field -> {
            assertThat(field.name()).isEqualTo("Up Next");
            assertThat(field.value()).contains("1. Hotel California");
            assertThat(field.value()).contains("2. Stairway to Heaven");
        });
    }

    @Test
    void embedOmitsUpNextFieldWhenQueueIsEmpty() {
        AtomicReference<EmbedMessage> sent = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(sent, new AtomicReference<>(), new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));

        assertThat(sent.get()).isNotNull();
        assertThat(sent.get().fields()).noneMatch(field -> field.name().equals("Up Next"));
    }

    @Test
    void upNextUpdatesWhenQueueUpdatedEventArrives() {
        AtomicReference<EmbedMessage> updated = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.QueueUpdated("player-1", "req-2", "guild-1",
                List.of(new TrackMetadata("New Song", "New Artist", "https://example.com/new", 120_000))));

        assertThat(updated.get()).isNotNull();
        assertThat(updated.get().fields()).anySatisfy(field -> {
            assertThat(field.name()).isEqualTo("Up Next");
            assertThat(field.value()).contains("New Song");
        });
    }

    @Test
    void truncatesLongTrackTitleAt30Characters() {
        AtomicReference<EmbedMessage> sent = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(sent, new AtomicReference<>(), new AtomicInteger(), Duration.ofSeconds(15));

        String longTitle = "A".repeat(100);
        announcer.accept(new MusicPlayerEvent.TrackStart("player-1", new Request("req-1", "@user"), "guild-1",
                new TrackMetadata(longTitle, "Artist", "https://example.com", 60_000), List.of()));

        assertThat(sent.get()).isNotNull();
        assertThat(sent.get().title()).hasSizeLessThanOrEqualTo(30);
        assertThat(sent.get().title()).endsWith("…");
    }

    @Test
    void truncatesLongAuthorAt40Characters() {
        AtomicReference<EmbedMessage> sent = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = announcer(sent, new AtomicReference<>(), new AtomicInteger(), Duration.ofSeconds(15));

        String longAuthor = "B".repeat(60);
        announcer.accept(new MusicPlayerEvent.TrackStart("player-1", new Request("req-1", "@user"), "guild-1",
                new TrackMetadata("Song", longAuthor, "https://example.com", 60_000), List.of()));

        assertThat(sent.get()).isNotNull();
        assertThat(sent.get().fields())
                .filteredOn(field -> field.name().equals("Author"))
                .singleElement()
                .satisfies(field -> {
                    assertThat(field.value()).hasSizeLessThanOrEqualTo(40);
                    assertThat(field.value()).endsWith("…");
                });
    }

    private static MusicPlayerTrackAnnouncer announcer(AtomicReference<EmbedMessage> sent,
                                                       AtomicReference<EmbedMessage> updated,
                                                       AtomicInteger sends,
                                                       Duration timeout) {
        return announcer(sent, updated, sends, new AtomicInteger(), timeout);
    }

    private static MusicPlayerTrackAnnouncer announcer(AtomicReference<EmbedMessage> sent,
                                                       AtomicReference<EmbedMessage> updated,
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

    private static DiscordClient discordClient(AtomicReference<EmbedMessage> sent,
                                               AtomicInteger sends,
                                               AtomicReference<EmbedMessage> updated,
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
                    public SentMessage send(EmbedMessage embed) {
                        sent.set(embed);
                        sends.incrementAndGet();
                        return new SentMessage() {
                            @Override
                            public void delete() {
                                deletes.incrementAndGet();
                            }

                            @Override
                            public void update(EmbedMessage newEmbed) {
                                updated.set(newEmbed);
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
}
