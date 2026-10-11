package com.bearify.controller.music.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.discord.api.message.ComponentMessage;
import com.bearify.discord.api.message.ComponentMessageBuilder;
import com.bearify.discord.api.message.ContainerBuilder;
import com.bearify.discord.api.message.InteractiveButton;
import com.bearify.music.player.bridge.model.TrackMetadata;

import java.awt.Color;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static com.bearify.discord.api.message.ButtonStyle.SECONDARY;

public class PlaybackComponent {

    public static final String PLAYER_PREVIOUS_LABEL = "❙◀︎◀︎";
    public static final String PLAYER_REWIND_LABEL = "◀︎";
    public static final String PLAYER_PAUSE_LABEL = "❚❚";
    public static final String PLAYER_PLAY_LABEL = "▶︎";
    public static final String PLAYER_FORWARD_LABEL = "▶︎";
    public static final String PLAYER_NEXT_LABEL = "▶︎▶︎❙";
    public static final String PLAYER_SHOW_QUEUE_LABEL = "Show";
    static final String LIVE_LABEL = BearifyEmoji.RED + " LIVE";

    private final AnnouncerProperties properties;

    public PlaybackComponent(AnnouncerProperties properties) {
        this.properties = properties;
    }

    public ComponentMessage render(PlaybackComponentState state) {
        ComponentMessageBuilder builder = ComponentMessage.builder("playback-announcer");
        appendNotification(builder, state);
        appendHero(builder, state);
        appendActionRow(builder, state);
        appendQueue(builder, state);
        state.footerText().ifPresent(footer -> builder.text("-# " + footer));
        return builder.build();
    }

    private void appendNotification(ComponentMessageBuilder builder, PlaybackComponentState state) {
        state.notification().ifPresent(notification -> builder.container(container -> container
                .accentColor(toDiscordColor(colorFor(notification.style())))
                .text("\uD83D\uDD14 " + notification.text())));
    }

    private static void appendHero(ComponentMessageBuilder builder, PlaybackComponentState state) {
        builder.container(container -> {
            container.text("-# **" + state.playbackState().header() + "**");
            state.artworkUri().ifPresent(uri -> container.image(uri.toString(), "Track artwork"));
            container.section(section -> {
                section.text("## " + truncate(state.track().title(), 45));
                section.linkButton(state.track().uri(), "Source");
            });
            container.text("### " + trackMetaLine(state.track(), 40, UnaryOperator.identity()));
            container.separator();
            container.text("-# **Requested by** " + state.requesterTag() + " · " + BearifyEmoji.SPEAKER + " " + state.volume() + "%");
        });
    }

    private static void appendActionRow(ComponentMessageBuilder builder, PlaybackComponentState state) {
        builder.container(container -> container.ActionRow(row -> {
            row.secondary("player:previous", PLAYER_PREVIOUS_LABEL);
            row.secondary("player:rewind", PLAYER_REWIND_LABEL);
            row.primary("player:pause-play", state.paused() ? PLAYER_PLAY_LABEL : PLAYER_PAUSE_LABEL);
            row.secondary("player:forward", PLAYER_FORWARD_LABEL);
            row.secondary("player:next", PLAYER_NEXT_LABEL);
        }));
    }

    private static void appendQueue(ComponentMessageBuilder builder, PlaybackComponentState state) {
        if (state.upNext().isEmpty()) {
            return;
        }
        builder.container(container -> {
            container.section(section -> {
                section.button(new InteractiveButton(SECONDARY, "player:clear", "Clear"));
                section.text("### Up Next");
            });
            container.spacer();
            TrackMetadata first = state.upNext().getFirst();
            container.text("""
                    **%s**
                    -# **%s**

                    """.formatted(
                    truncate(first.title(), 50),
                    trackMetaLine(first, 50, UnaryOperator.identity())
            ));
            List<TrackMetadata> rest = state.upNext().subList(1, state.upNext().size());
            if (!rest.isEmpty()) {
                container.separator();
                appendTrackRows(container, rest, UnaryOperator.identity());
            }
            int more = state.queueSize() - state.upNext().size();
            if (more > 0) {
                container.separator();
                container.spacer();
                container.section(section -> {
                    section.button(new InteractiveButton(SECONDARY, "player:queue", PLAYER_SHOW_QUEUE_LABEL));
                    section.text("-# +" + more + " more on the trail" + moreLength(state).map(length -> " · " + length).orElse(""));
                });
            }
        });
    }

    /** Lists tracks the way Up Next lists all but its first; {@code clean} rewrites each title and author after truncation. */
    static void appendTrackRows(ContainerBuilder container, List<TrackMetadata> tracks, UnaryOperator<String> clean) {
        for (int index = 0; index < tracks.size(); index++) {
            TrackMetadata track = tracks.get(index);
            container.text("""
                    %s
                    -# %s
                    """.formatted(
                    clean.apply(truncate(track.title(), 50)),
                    trackMetaLine(track, 50, clean)
            ));
            if (index + 1 < tracks.size()) {
                container.separator();
            }
        }
    }

    // the queue's length minus what Up Next already shows; unknown when the bear didn't say
    private static Optional<String> moreLength(PlaybackComponentState state) {
        long shownMs = state.upNext().stream().filter(track -> !track.isLive()).mapToLong(TrackMetadata::durationMs).sum();
        Duration more = state.queueDuration().minusMillis(shownMs);
        return more.isPositive() ? Optional.of(total(more)) : Optional.empty();
    }

    private Color colorFor(PlaybackComponentState.NotificationStyle style) {
        return switch (style) {
            case ERROR -> new Color(properties.colorErrorInt());
            case INFO -> new Color(properties.colorNowPlayingInt());
        };
    }

    private static int toDiscordColor(Color color) {
        return color.getRGB() & 0xFFFFFF;
    }

    private static String trackMetaLine(TrackMetadata track, int authorLimit, UnaryOperator<String> clean) {
        return clean.apply(truncate(track.author(), authorLimit)) + " \u2022 " + length(track);
    }

    static String length(TrackMetadata track) {
        return track.isLive() ? LIVE_LABEL : humanReadable(Duration.ofMillis(track.durationMs()));
    }

    /** A queue's length: hours once it passes one, unlike a single track's. */
    static String total(Duration duration) {
        if (duration.toHours() > 0) {
            return "%dh %02dm".formatted(duration.toHours(), duration.toMinutesPart());
        }
        return humanReadable(duration);
    }

    static String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        if (text.length() <= limit) {
            return text;
        }
        int end = limit - 1;
        // never keep half of an emoji: cutting between a surrogate pair leaves an invalid character
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + "\u2026";
    }

    static String humanReadable(Duration duration) {
        long totalSeconds = duration.toSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes > 0) {
            return "%dm %02ds".formatted(minutes, seconds);
        }
        return "%ds".formatted(seconds);
    }
}
