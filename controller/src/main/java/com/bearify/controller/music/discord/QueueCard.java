package com.bearify.controller.music.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.discord.api.message.ComponentMessage;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.TrackMetadata;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.bearify.controller.music.discord.AnsweringListener.NO_PLAYER_MESSAGE;
import static com.bearify.controller.music.discord.AnsweringListener.TIMED_OUT_MESSAGE;
import static com.bearify.controller.music.discord.PlaybackComponent.truncate;

/** The queue as a private card in the player's Up Next style; what isn't a list stays plain text. */
final class QueueCard {

    static final String TRAIL_EMPTY_MESSAGE = BearifyEmoji.HONEY + " | The trail is empty. Add something with `/player play`!";
    static final String NOTHING_QUEUED_MESSAGE = "Nothing queued after this one.";
    static final String NOTHING_FURTHER_MESSAGE = BearifyEmoji.HONEY + " | Nothing else on the trail.";
    static final String UP_NEXT_HEADER = "### Up Next";
    static final String FURTHER_HEADER = "### Further up the trail";

    // the bear sends the player message the first three upcoming tracks, so Show starts after them
    static final int SHOWN_ON_PLAYER = 3;
    private static final int MAX_ROWS = 10;
    // a title like "*NSYNC" or "||secret||" would otherwise format the rest of the card
    private static final Pattern MARKDOWN = Pattern.compile("[\\\\*_~|`>\\[\\]]");

    /** What to answer with: a plain text, or a card. */
    sealed interface Reply permits Text, Card {}

    record Text(String text) implements Reply {}

    record Card(ComponentMessage message) implements Reply {}

    private QueueCard() {
    }

    /** {@code /player queue}: every upcoming track, under the one playing now. */
    static Reply wholeQueue(Optional<MusicPlayerEvent> reply) {
        return answer(reply, snapshot -> {
            if (snapshot.current().isEmpty() && snapshot.upcoming().isEmpty()) {
                return new Text(TRAIL_EMPTY_MESSAGE);
            }
            if (snapshot.upcoming().isEmpty()) {
                TrackMetadata current = snapshot.current().orElseThrow();
                return new Text(BearifyEmoji.NOTES + " | Now playing: **" + escaped(truncate(current.title(), 50)) + "** — "
                        + escaped(truncate(current.author(), 50)) + "\n\n" + NOTHING_QUEUED_MESSAGE);
            }
            return new Card(card(UP_NEXT_HEADER, snapshot.current(), snapshot.upcoming()));
        });
    }

    /** The player's Show button: only the tracks the player message doesn't list. */
    static Reply beyondPlayer(Optional<MusicPlayerEvent> reply) {
        return answer(reply, snapshot -> {
            List<TrackMetadata> upcoming = snapshot.upcoming();
            // the queue may have shrunk since the player message showed its Show button
            if (upcoming.size() <= SHOWN_ON_PLAYER) {
                return new Text(NOTHING_FURTHER_MESSAGE);
            }
            return new Card(card(FURTHER_HEADER, Optional.empty(), upcoming.subList(SHOWN_ON_PLAYER, upcoming.size())));
        });
    }

    private static Reply answer(Optional<MusicPlayerEvent> reply, Function<MusicPlayerEvent.QueueSnapshot, Reply> onSnapshot) {
        return reply.map(event -> switch (event) {
            case MusicPlayerEvent.QueueSnapshot snapshot -> onSnapshot.apply(snapshot);
            case MusicPlayerEvent.PlayerNotFound ignored -> new Text(NO_PLAYER_MESSAGE);
            default -> new Text(TIMED_OUT_MESSAGE);
        }).orElse(new Text(TIMED_OUT_MESSAGE));
    }

    private static ComponentMessage card(String header, Optional<TrackMetadata> current, List<TrackMetadata> tracks) {
        List<TrackMetadata> rows = tracks.subList(0, Math.min(MAX_ROWS, tracks.size()));
        return ComponentMessage.builder("queue").container(container -> {
            container.text(header);
            current.ifPresent(track -> container.text("-# Now playing: " + escaped(truncate(track.title(), 50))
                    + " — " + escaped(truncate(track.author(), 50))));
            container.spacer();
            PlaybackComponent.appendTrackRows(container, rows, QueueCard::escaped);
            container.separator();
            int unlisted = tracks.size() - rows.size();
            if (unlisted > 0) {
                container.text("-# …and " + unlisted + " more");
            }
            container.spacer();
            container.text(footer(tracks));
        }).build();
    }

    private static String footer(List<TrackMetadata> tracks) {
        String count = tracks.size() == 1 ? "1 track" : tracks.size() + " tracks";
        List<Long> known = tracks.stream().filter(track -> !track.isLive()).map(TrackMetadata::durationMs).toList();
        // live streams have no length to add up
        String total = known.isEmpty() ? "" : " · " + PlaybackComponent.total(Duration.ofMillis(known.stream().mapToLong(Long::longValue).sum()));
        return "-# " + count + total;
    }

    private static String escaped(String text) {
        return MARKDOWN.matcher(text).replaceAll("\\\\$0");
    }
}
