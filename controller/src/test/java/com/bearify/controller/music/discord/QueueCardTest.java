package com.bearify.controller.music.discord;

import com.bearify.discord.api.message.ActionRow;
import com.bearify.discord.api.message.ComponentMessage;
import com.bearify.discord.api.message.Container;
import com.bearify.discord.api.message.ContainerChild;
import com.bearify.discord.api.message.Divider;
import com.bearify.discord.api.message.ImageBlock;
import com.bearify.discord.api.message.Section;
import com.bearify.discord.api.message.TextBlock;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.TrackMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class QueueCardTest {

    private static final TrackMetadata CURRENT = new TrackMetadata("Never Gonna Give You Up", "Rick Astley", "https://example.com/rick", 213_000);
    private static final long LIVE = Long.MAX_VALUE;

    @Test
    void listsWholeQueueUnderNowPlayingForCommand() {
        QueueCard.Reply reply = QueueCard.wholeQueue(snapshot(Optional.of(CURRENT), tracks(4, 60_000)));

        assertThat(texts(reply)).containsExactly(
                "### Up Next",
                "-# Now playing: Never Gonna Give You Up — Rick Astley",
                "Song 1\n-# Artist • 1m 00s\n",
                "Song 2\n-# Artist • 1m 00s\n",
                "Song 3\n-# Artist • 1m 00s\n",
                "Song 4\n-# Artist • 1m 00s\n",
                "-# 4 tracks · 4m 00s");
    }

    @Test
    void startsAfterThePlayersThreeTracksForShowButton() {
        QueueCard.Reply reply = QueueCard.beyondPlayer(snapshot(Optional.of(CURRENT), tracks(5, 60_000)));

        assertThat(texts(reply)).containsExactly(
                "### Further up the trail",
                "Song 4\n-# Artist • 1m 00s\n",
                "Song 5\n-# Artist • 1m 00s\n",
                "-# 2 tracks · 2m 00s");
    }

    @Test
    void separatesRowsLikeThePlayersUpNext() {
        QueueCard.Reply reply = QueueCard.beyondPlayer(snapshot(Optional.of(CURRENT), tracks(5, 60_000)));

        List<ContainerChild> children = card(reply).containers().getFirst().children();
        assertThat(children).extracting(child -> child.getClass().getSimpleName())
                .containsExactly("TextBlock", "Divider", "TextBlock", "Divider", "TextBlock", "Divider", "TextBlock");
    }

    @Test
    void listsTenRowsThenSaysHowManyMore() {
        QueueCard.Reply reply = QueueCard.wholeQueue(snapshot(Optional.of(CURRENT), tracks(45, 60_000)));

        List<String> texts = texts(reply);
        assertThat(texts).filteredOn(text -> text.startsWith("Song ")).hasSize(10);
        assertThat(texts).contains("Song 10\n-# Artist • 1m 00s\n").doesNotContain("Song 11\n-# Artist • 1m 00s\n");
        assertThat(texts.getLast()).isEqualTo("-# …and 35 more\n-# 45 tracks · 45m 00s");
    }

    @Test
    void staysUnderFortyComponentsWithFullCard() {
        QueueCard.Reply reply = QueueCard.wholeQueue(snapshot(Optional.of(CURRENT), tracks(45, 60_000)));

        assertThat(componentCount(card(reply))).isLessThanOrEqualTo(40);
    }

    @Test
    void formatsTotalWithHours() {
        QueueCard.Reply reply = QueueCard.wholeQueue(snapshot(Optional.of(CURRENT), tracks(3, 3_220_000)));

        assertThat(texts(reply).getLast()).isEqualTo("-# 3 tracks · 2h 41m");
    }

    @Test
    void showsLiveAndLeavesItOutOfTotal() {
        List<TrackMetadata> upcoming = List.of(
                new TrackMetadata("Radio", "Station", "https://example.com/radio", LIVE),
                new TrackMetadata("Song", "Artist", "https://example.com/song", 90_000));

        List<String> texts = texts(QueueCard.wholeQueue(snapshot(Optional.of(CURRENT), upcoming)));

        assertThat(texts).contains("Radio\n-# Station • LIVE\n");
        assertThat(texts.getLast()).isEqualTo("-# 2 tracks · 1m 30s");
    }

    @Test
    void leavesTotalOutWhenOnlyLiveStreamsAreQueued() {
        List<TrackMetadata> upcoming = List.of(new TrackMetadata("Radio", "Station", "https://example.com/radio", LIVE));

        assertThat(texts(QueueCard.wholeQueue(snapshot(Optional.of(CURRENT), upcoming))).getLast()).isEqualTo("-# 1 track");
    }

    @Test
    void escapesMarkdownInTitlesAndAuthors() {
        List<TrackMetadata> upcoming = List.of(new TrackMetadata("*NSYNC ||x|| ~~y~~ `z` [a](b) > q _u_ \\", "a|b", "https://example.com/1", 60_000));

        List<String> texts = texts(QueueCard.wholeQueue(snapshot(Optional.of(new TrackMetadata("**Bold**", "_me_", "https://example.com/now", 60_000)), upcoming)));

        assertThat(texts).contains("-# Now playing: \\*\\*Bold\\*\\* — \\_me\\_");
        assertThat(texts).contains("\\*NSYNC \\|\\|x\\|\\| \\~\\~y\\~\\~ \\`z\\` \\[a\\](b) \\> q \\_u\\_ \\\\\n-# a\\|b • 1m 00s\n");
    }

    @Test
    void saysTrailIsEmptyWhenNothingPlays() {
        assertThat(QueueCard.wholeQueue(snapshot(Optional.empty(), List.of())))
                .isEqualTo(new QueueCard.Text("🍯 | The trail is empty. Add something with `/player play`!"));
    }

    @Test
    void saysNothingQueuedAfterCurrentTrack() {
        assertThat(QueueCard.wholeQueue(snapshot(Optional.of(CURRENT), List.of())))
                .isEqualTo(new QueueCard.Text("🎶 | Now playing: **Never Gonna Give You Up** — Rick Astley\n\nNothing queued after this one."));
    }

    @Test
    void saysNothingElseWhenQueueShrankToWhatThePlayerShows() {
        assertThat(QueueCard.beyondPlayer(snapshot(Optional.of(CURRENT), tracks(3, 60_000))))
                .isEqualTo(new QueueCard.Text("🍯 | Nothing else on the trail."));
    }

    @Test
    void answersNoPlayerWhenBearHasNoPlayer() {
        Optional<MusicPlayerEvent> notFound = Optional.of(new MusicPlayerEvent.PlayerNotFound("player-1", "req-1", "guild-1"));

        assertThat(QueueCard.wholeQueue(notFound)).isEqualTo(new QueueCard.Text(AnsweringListener.NO_PLAYER_MESSAGE));
        assertThat(QueueCard.beyondPlayer(notFound)).isEqualTo(new QueueCard.Text(AnsweringListener.NO_PLAYER_MESSAGE));
    }

    @Test
    void answersTimedOutWhenBearDoesNotAnswer() {
        assertThat(QueueCard.wholeQueue(Optional.empty())).isEqualTo(new QueueCard.Text(AnsweringListener.TIMED_OUT_MESSAGE));
        assertThat(QueueCard.beyondPlayer(Optional.empty())).isEqualTo(new QueueCard.Text(AnsweringListener.TIMED_OUT_MESSAGE));
    }

    private static Optional<MusicPlayerEvent> snapshot(Optional<TrackMetadata> current, List<TrackMetadata> upcoming) {
        return Optional.of(new MusicPlayerEvent.QueueSnapshot("player-1", "req-1", "guild-1", current, upcoming));
    }

    private static List<TrackMetadata> tracks(int count, long durationMs) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new TrackMetadata("Song " + i, "Artist", "https://example.com/" + i, durationMs))
                .toList();
    }

    private static ComponentMessage card(QueueCard.Reply reply) {
        assertThat(reply).isInstanceOf(QueueCard.Card.class);
        return ((QueueCard.Card) reply).message();
    }

    private static List<String> texts(QueueCard.Reply reply) {
        return card(reply).containers().stream()
                .flatMap(container -> container.children().stream())
                .filter(TextBlock.class::isInstance)
                .map(child -> ((TextBlock) child).text())
                .toList();
    }

    // Discord counts every component, nested ones included, against its 40-per-message limit
    private static int componentCount(ComponentMessage message) {
        return message.items().stream().mapToInt(item -> switch (item) {
            case Container container -> 1 + container.children().stream().mapToInt(QueueCardTest::componentCount).sum();
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
        };
    }
}
