package com.bearify.controller.music.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.controller.music.domain.MusicPlayerEventConsumer;
import com.bearify.controller.music.domain.RecordingPlayerMessageStore;
import com.bearify.controller.music.domain.PlayerMessageStore.PlayerMessage;
import com.bearify.controller.music.domain.PlayerMessageStore;
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
import com.bearify.music.player.bridge.model.StopReason;
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

import static com.bearify.controller.music.discord.PlaybackComponent.*;
import static org.assertj.core.api.Assertions.assertThat;

class DiscordPlaybackAnnouncerTest {

    private static final String PLAYER_ID = "player-1";
    private static final String GUILD_ID = "guild-1";
    private static final String TEXT_CHANNEL_ID = "text-1";
    private static final String MESSAGE_ID = "message-1";


    @Test
    void defaultFooterStartsWithBearEmoji() {
        AnnouncerProperties props = new AnnouncerProperties(
                "#FFA500",
                "#FF4444",
                "\uD83D\uDC3B Bearify \u2022 Powered by Bearable Software",
                Duration.ofSeconds(15)
        );

        assertThat(props.footer()).startsWith(BearifyEmoji.BEAR + " Bearify");
    }

    @Test
    void acceptPostsComponentMessageWhenTrackStartsAndNoMessageExists() {
        AtomicReference<ComponentMessage> sent = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = announcer(sent, new AtomicReference<>(), new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));

        assertThat(sent.get()).isNotNull();
        assertThat(sent.get().label()).isEqualTo("playback-announcer");
        assertThat(sent.get().containers()).hasSize(2);
        assertThat(allTexts(sent.get())).anyMatch(text -> text.contains("Song"));
        assertThat(buttonLabels(sent.get())).containsExactly(
                PLAYER_PREVIOUS_LABEL,
                PLAYER_REWIND_LABEL,
                PLAYER_PAUSE_LABEL,
                PLAYER_FORWARD_LABEL,
                PLAYER_NEXT_LABEL
        );
    }

    @Test
    void acceptShowsPausedStateAfterPausedEvent() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Paused("player-1", new Request("req-2", "@user"), "guild-1"));

        assertThat(updated.get()).isNotNull();
        assertThat(allTexts(updated.get())).anyMatch(text -> text.contains("ON THE AIR"));
        assertThat(buttonLabels(updated.get())).containsExactly(
                PLAYER_PREVIOUS_LABEL,
                PLAYER_REWIND_LABEL,
                PLAYER_PLAY_LABEL,
                PLAYER_FORWARD_LABEL,
                PLAYER_NEXT_LABEL
        );
    }

    @Test
    void acceptRestoresPlayingCopyAfterResume() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Paused("player-1", new Request("req-2", "@user"), "guild-1"));
        announcer.accept(new MusicPlayerEvent.Resumed("player-1", new Request("req-3", "@user"), "guild-1"));

        assertThat(updated.get()).isNotNull();
        assertThat(allTexts(updated.get())).anyMatch(text -> text.contains("ON THE AIR"));
        assertThat(buttonLabels(updated.get())).containsExactly(
                PLAYER_PREVIOUS_LABEL,
                PLAYER_REWIND_LABEL,
                PLAYER_PAUSE_LABEL,
                PLAYER_FORWARD_LABEL,
                PLAYER_NEXT_LABEL
        );
    }

    @Test
    void acceptShowsTemporarySkippedAction() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Skipped("player-1", new Request("req-2", "@user"), "guild-1"));

        assertThat(allTexts(updated.get())).anyMatch(text -> text.contains("Skipped by @user"));
    }

    @Test
    void acceptShowsTemporaryWentBackAction() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.WentBack("player-1", new Request("req-2", "@user"), "guild-1"));

        assertThat(allTexts(updated.get())).anyMatch(text -> text.contains("Jumped back by @user"));
    }

    @Test
    void showsRestartedLabelWithRequester() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Restarted("player-1", new Request("req-2", "@user"), "guild-1"));

        assertThat(allTexts(updated.get())).anyMatch(text -> text.contains("Restarted by @user"));
    }

    @Test
    void acceptReplacesOlderTemporaryActionWithNewerOne() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Skipped("player-1", new Request("req-2", "@user"), "guild-1"));
        announcer.accept(new MusicPlayerEvent.Cleared("player-1", new Request("req-3", "@other"), "guild-1", List.of()));

        assertThat(allTexts(updated.get())).anyMatch(text -> text.contains("Queue cleared by @other"));
        assertThat(allTexts(updated.get())).noneMatch(text -> text.contains("Skipped by @user"));
    }

    @Test
    void acceptClearsTemporaryActionAfterTimeout() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofMillis(50));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Forwarded("player-1", new Request("req-2", "@user"), "guild-1", 30_000));

        Awaitility.await().atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                assertThat(updated.get()).isNotNull());
        Awaitility.await().atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                assertThat(allTexts(updated.get())).noneMatch(text -> text.contains("Forwarded by @user")));
    }

    @Test
    void staleClearDoesNotRemoveNewerLabel() throws InterruptedException {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofMillis(500));

        announcer.accept(trackStart("player-1"));
        synchronized (announcer) {
            announcer.accept(new MusicPlayerEvent.Skipped("player-1", new Request("req-2", "@user"), "guild-1"));
            // Let the skip's clear fire and block on the announcer's lock
            Thread.sleep(600);
            announcer.accept(new MusicPlayerEvent.Forwarded("player-1", new Request("req-3", "@other"), "guild-1", 30_000));
        }

        // Give the stale clear time to run now that the lock is free
        Thread.sleep(100);
        assertThat(allTexts(updated.get())).anyMatch(text -> text.contains("Forwarded by @other"));
    }

    @Test
    void clearsTemporaryActionOffTheSchedulerThread() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        AtomicReference<Thread> updatingThread = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = new DiscordPlaybackAnnouncer(
                discordClient(new AtomicReference<>(), new AtomicInteger(), updated, new AtomicInteger(), updatingThread),
                new AnnouncerProperties("#FFA500", "#FF4444", "Bearify", Duration.ofMillis(50)),
                new RecordingPlayerMessageStore(), PLAYER_ID, GUILD_ID, TEXT_CHANNEL_ID);

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Forwarded("player-1", new Request("req-2", "@user"), "guild-1", 30_000));

        Awaitility.await().atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                assertThat(allTexts(updated.get())).noneMatch(text -> text.contains("Forwarded by @user")));
        assertThat(updatingThread.get().isVirtual()).isTrue();
    }

    @Test
    void acceptUpdatesExistingMessageWhenTrackErrorArrives() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        AtomicInteger sends = new AtomicInteger();
        MusicPlayerEventConsumer announcer = announcer(new AtomicReference<>(), updated, sends, Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(trackError("player-1"));

        assertThat(sends.get()).isEqualTo(1);
        assertThat(allTexts(updated.get())).anyMatch(text -> text.contains("Something went wrong loading this track"));
        assertThat(updated.get().containers().getFirst().accentColor()).isEqualTo(0xFF4444);
    }

    @Test
    void acceptDeletesExistingMessageOnStopped() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        AtomicInteger sends = new AtomicInteger();
        AtomicInteger deletes = new AtomicInteger();
        MusicPlayerEventConsumer announcer = announcer(new AtomicReference<>(), updated, sends, deletes, Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Stopped("player-1", "req-2", "guild-1", StopReason.REQUESTED));

        assertThat(sends.get()).isEqualTo(1);
        assertThat(deletes.get()).isEqualTo(1);
    }

    @Test
    void acceptDoesNotDeleteMessageForUnrelatedNonTerminalEvent() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        AtomicInteger sends = new AtomicInteger();
        AtomicInteger deletes = new AtomicInteger();
        MusicPlayerEventConsumer announcer = announcer(new AtomicReference<>(), updated, sends, deletes, Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.NothingToGoBack("player-1", "req-2", "guild-1"));

        assertThat(sends.get()).isEqualTo(1);
        assertThat(deletes.get()).isZero();
    }

    @Test
    void includesUpNextFromTrackStart() {
        AtomicReference<ComponentMessage> sent = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = announcer(sent, new AtomicReference<>(), new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStartWithUpNext("player-1"));

        assertThat(allTexts(sent.get())).anyMatch(text -> text.contains("## Up Next"));
        assertThat(allTexts(sent.get())).anyMatch(text -> text.contains("Hotel California"));
        assertThat(allTexts(sent.get())).anyMatch(text -> text.contains("Stairway to Heaven"));
    }

    @Test
    void omitsUpNextWhenQueueIsEmpty() {
        AtomicReference<ComponentMessage> sent = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = announcer(sent, new AtomicReference<>(), new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));

        assertThat(allTexts(sent.get())).noneMatch(text -> text.contains("**Up Next**"));
    }

    @Test
    void upNextUpdatesWhenQueueUpdatedEventArrives() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.QueueUpdated("player-1", "req-2", "guild-1",
                List.of(new TrackMetadata("New Song", "New Artist", "https://example.com/new", 120_000)), 1, 120_000L));

        assertThat(allTexts(updated.get())).anyMatch(text -> text.contains("New Song"));
    }

    @Test
    void showsHowManyMoreAreQueuedWhenBearReportsALongerQueue() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));
        List<TrackMetadata> upNext = List.of(
                new TrackMetadata("One", "Artist", "https://example.com/1", 60_000),
                new TrackMetadata("Two", "Artist", "https://example.com/2", 60_000),
                new TrackMetadata("Three", "Artist", "https://example.com/3", 60_000));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.QueueUpdated("player-1", "req-2", "guild-1", upNext, 7, 900_000L));

        assertThat(allTexts(updated.get())).contains("-# +4 more on the trail · 12m 00s");
    }

    @Test
    void clearedEventRemovesUpNextWhenQueueBecomesEmpty() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStartWithUpNext("player-1"));
        announcer.accept(new MusicPlayerEvent.Cleared("player-1", new Request("req-2", "@user"), "guild-1", List.of()));

        assertThat(allTexts(updated.get())).noneMatch(text -> text.contains("**Up Next**"));
        assertThat(allTexts(updated.get())).anyMatch(text -> text.contains("Queue cleared by @user"));
    }

    @Test
    void truncatesLongTrackTitleAt45Characters() {
        AtomicReference<ComponentMessage> sent = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = announcer(sent, new AtomicReference<>(), new AtomicInteger(), Duration.ofSeconds(15));

        String longTitle = "A".repeat(100);
        announcer.accept(new MusicPlayerEvent.TrackStart("player-1", new Request("req-1", "@user"), "guild-1",
                new TrackMetadata(longTitle, "Artist", "https://example.com", 60_000), List.of(), 0, 0L));

        assertThat(allTexts(sent.get())).anyMatch(text -> text.contains("A".repeat(44)));
    }

    @Test
    void truncatesLongAuthorAt40Characters() {
        AtomicReference<ComponentMessage> sent = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = announcer(sent, new AtomicReference<>(), new AtomicInteger(), Duration.ofSeconds(15));

        String longAuthor = "B".repeat(60);
        announcer.accept(new MusicPlayerEvent.TrackStart("player-1", new Request("req-1", "@user"), "guild-1",
                new TrackMetadata("Song", longAuthor, "https://example.com", 60_000), List.of(), 0, 0L));

        assertThat(allTexts(sent.get())).anyMatch(text -> text.contains("B".repeat(39)));
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

    private static MusicPlayerEventConsumer announcer(AtomicReference<ComponentMessage> sent,
                                               AtomicReference<ComponentMessage> updated,
                                               AtomicInteger sends,
                                               Duration timeout) {
        return announcer(sent, updated, sends, new AtomicInteger(), timeout);
    }

    private static MusicPlayerEventConsumer announcer(AtomicReference<ComponentMessage> sent,
                                               AtomicReference<ComponentMessage> updated,
                                               AtomicInteger sends,
                                               AtomicInteger deletes,
                                               Duration timeout) {
        return new DiscordPlaybackAnnouncer(
                discordClient(sent, sends, updated, deletes, new AtomicReference<>()),
                new AnnouncerProperties("#FFA500", "#FF4444", "Bearify", timeout),
                new RecordingPlayerMessageStore(), PLAYER_ID, GUILD_ID, TEXT_CHANNEL_ID);
    }

    @Test
    void showsNobodyListeningNoticeWhileAutoPaused() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.AutoPaused("player-1", "req-2", "guild-1"));

        assertThat(allTexts(updated.get())).contains("\uD83D\uDD14 Paused: it's lonely in here");
        assertThat(buttonLabels(updated.get())).contains(PLAYER_PLAY_LABEL);
    }

    @Test
    void clearsNoticeWhenAutoResumed() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.AutoPaused("player-1", "req-2", "guild-1"));
        announcer.accept(new MusicPlayerEvent.AutoResumed("player-1", "req-3", "guild-1"));

        assertThat(allTexts(updated.get())).noneMatch(text -> text.contains(DiscordPlaybackAnnouncer.NOBODY_LISTENING_NOTICE));
        assertThat(buttonLabels(updated.get())).contains(PLAYER_PAUSE_LABEL);
    }

    @Test
    void clearsNoticeWhenNextTrackStarts() {
        AtomicReference<ComponentMessage> updated = new AtomicReference<>();
        MusicPlayerEventConsumer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.AutoPaused("player-1", "req-2", "guild-1"));
        announcer.accept(trackStart("player-1"));

        assertThat(allTexts(updated.get())).noneMatch(text -> text.contains(DiscordPlaybackAnnouncer.NOBODY_LISTENING_NOTICE));
    }

    @Test
    void postsLeaveNoteWhenStoppedForNobodyListening() {
        List<String> texts = new ArrayList<>();
        AtomicInteger deletes = new AtomicInteger();
        MusicPlayerEventConsumer announcer = announcerWithTexts(deletes, texts);

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Stopped("player-1", "req-2", "guild-1", StopReason.NOBODY_LISTENING));

        assertThat(deletes.get()).isEqualTo(1);
        assertThat(texts).containsExactly(DiscordPlaybackAnnouncer.NOBODY_LISTENING_LEAVE_NOTE);
    }

    @Test
    void postsLeaveNoteWhenStoppedForIdle() {
        List<String> texts = new ArrayList<>();
        MusicPlayerEventConsumer announcer = announcerWithTexts(new AtomicInteger(), texts);

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Stopped("player-1", "req-2", "guild-1", StopReason.IDLE));

        assertThat(texts).containsExactly(DiscordPlaybackAnnouncer.IDLE_LEAVE_NOTE);
    }

    @Test
    void postsLeaveNoteWhenStoppedForIdleAfterQueueEnded() {
        List<String> texts = new ArrayList<>();
        AtomicInteger deletes = new AtomicInteger();
        MusicPlayerEventConsumer announcer = announcerWithTexts(deletes, texts);

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.QueueEmpty("player-1", "req-2", "guild-1"));
        announcer.accept(new MusicPlayerEvent.Stopped("player-1", "req-3", "guild-1", StopReason.IDLE));

        assertThat(deletes.get()).isEqualTo(1);
        assertThat(texts).containsExactly(DiscordPlaybackAnnouncer.IDLE_LEAVE_NOTE);
    }

    @Test
    void postsLeaveNoteWhenStoppedForDisconnect() {
        List<String> texts = new ArrayList<>();
        AtomicInteger deletes = new AtomicInteger();
        MusicPlayerEventConsumer announcer = announcerWithTexts(deletes, texts);

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Stopped("player-1", "req-2", "guild-1", StopReason.DISCONNECTED));

        assertThat(deletes.get()).isEqualTo(1);
        assertThat(texts).containsExactly(DiscordPlaybackAnnouncer.DISCONNECTED_LEAVE_NOTE);
    }

    @Test
    void postsNoLeaveNoteWhenStopRequested() {
        List<String> texts = new ArrayList<>();
        MusicPlayerEventConsumer announcer = announcerWithTexts(new AtomicInteger(), texts);

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Stopped("player-1", "req-2", "guild-1", StopReason.REQUESTED));

        assertThat(texts).isEmpty();
    }

    private static MusicPlayerEventConsumer announcerWithTexts(AtomicInteger deletes, List<String> texts) {
        return announcerWithStore(deletes, texts, new RecordingPlayerMessageStore());
    }

    private static MusicPlayerEventConsumer announcerWithStore(AtomicInteger deletes, List<String> texts, PlayerMessageStore store) {
        return new DiscordPlaybackAnnouncer(
                discordClient(new AtomicReference<>(), new AtomicInteger(), new AtomicReference<>(), deletes, new AtomicReference<>(), texts),
                new AnnouncerProperties("#FFA500", "#FF4444", "Bearify", Duration.ofSeconds(15)),
                store, PLAYER_ID, GUILD_ID, TEXT_CHANNEL_ID);
    }

    @Test
    void remembersMessageWhenPosting() {
        RecordingPlayerMessageStore store = new RecordingPlayerMessageStore();
        MusicPlayerEventConsumer announcer = announcerWithStore(new AtomicInteger(), new ArrayList<>(), store);

        announcer.accept(trackStart(PLAYER_ID));

        assertThat(store.all()).containsExactly(new PlayerMessage(PLAYER_ID, GUILD_ID, TEXT_CHANNEL_ID, MESSAGE_ID));
    }

    @Test
    void forgetsMessageWhenDeleting() {
        RecordingPlayerMessageStore store = new RecordingPlayerMessageStore();
        MusicPlayerEventConsumer announcer = announcerWithStore(new AtomicInteger(), new ArrayList<>(), store);

        announcer.accept(trackStart(PLAYER_ID));
        announcer.accept(new MusicPlayerEvent.QueueEmpty(PLAYER_ID, "req-2", GUILD_ID));

        assertThat(store.all()).isEmpty();
    }

    @Test
    void postsLostNoteAndDeletesMessageWhenBearIsLost() {
        AtomicInteger deletes = new AtomicInteger();
        List<String> texts = new ArrayList<>();
        MusicPlayerEventConsumer announcer = announcerWithTexts(deletes, texts);

        announcer.accept(trackStart(PLAYER_ID));
        announcer.accept(new MusicPlayerEvent.Stopped(PLAYER_ID, "req-2", GUILD_ID, StopReason.LOST));

        assertThat(deletes.get()).isEqualTo(1);
        assertThat(texts).containsExactly(DiscordPlaybackAnnouncer.LOST_LEAVE_NOTE);
    }

    private static MusicPlayerEvent.TrackStart trackStart(String playerId) {
        return new MusicPlayerEvent.TrackStart(playerId, new Request("req-1", "@user"), "guild-1",
                new TrackMetadata("Song", "Artist", "https://example.com", 60_000), List.of(), 0, 0L);
    }

    private static MusicPlayerEvent.TrackStart trackStartWithUpNext(String playerId) {
        return new MusicPlayerEvent.TrackStart(playerId, new Request("req-1", "@user"), "guild-1",
                new TrackMetadata("Song", "Artist", "https://example.com", 60_000),
                List.of(
                        new TrackMetadata("Hotel California", "Eagles", "https://example.com/1", 390_000),
                        new TrackMetadata("Stairway to Heaven", "Led Zeppelin", "https://example.com/2", 482_000)
                ), 2, 872_000L);
    }

    private static MusicPlayerEvent.TrackError trackError(String playerId) {
        return new MusicPlayerEvent.TrackError(playerId, "req-2", "guild-1",
                new TrackMetadata("Song", "Artist", "https://example.com", 60_000));
    }

    private static DiscordClient discordClient(AtomicReference<ComponentMessage> sent,
                                               AtomicInteger sends,
                                               AtomicReference<ComponentMessage> updated,
                                               AtomicInteger deletes,
                                               AtomicReference<Thread> updatingThread) {
        return discordClient(sent, sends, updated, deletes, updatingThread, new ArrayList<>());
    }

    private static DiscordClient discordClient(AtomicReference<ComponentMessage> sent,
                                               AtomicInteger sends,
                                               AtomicReference<ComponentMessage> updated,
                                               AtomicInteger deletes,
                                               AtomicReference<Thread> updatingThread,
                                               List<String> texts) {
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
                    public boolean isInVoice(String userId) {
                        return false;
                    }

                    @Override
                    public void join(String channelId, AudioProvider provider, VoiceSessionListener onJoined) {
                    }
                };
            }

            @Override
            public String selfUserId() {
                return "test-bot-user";
            }

            @Override
            public TextChannel textChannel(String channelId) {
                return new TextChannel() {
                    @Override
                    public void send(String message) {
                        texts.add(message);
                    }

                    @Override
                    public SentMessage send(ComponentMessage message) {
                        sent.set(message);
                        sends.incrementAndGet();
                        return new SentMessage() {
                            @Override
                            public String id() {
                                return MESSAGE_ID;
                            }

                            @Override
                            public void delete() {
                                deletes.incrementAndGet();
                            }

                            @Override
                            public void update(ComponentMessage updatedMessage) {
                                updatingThread.set(Thread.currentThread());
                                updated.set(updatedMessage);
                            }
                        };
                    }

                    @Override
                    public void delete(String messageId) {
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


    @Test
    void forgetsMessageWhenStopped() {
        RecordingPlayerMessageStore store = new RecordingPlayerMessageStore();
        MusicPlayerEventConsumer announcer = announcerWithStore(new AtomicInteger(), new ArrayList<>(), store);

        announcer.accept(trackStart(PLAYER_ID));
        announcer.accept(new MusicPlayerEvent.Stopped(PLAYER_ID, "req-2", GUILD_ID, StopReason.LOST));

        assertThat(store.all()).isEmpty();
    }
}
