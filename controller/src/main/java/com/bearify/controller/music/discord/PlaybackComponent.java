package com.bearify.controller.music.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.discord.api.format.TextCut;
import com.bearify.discord.api.message.ComponentMessage;
import com.bearify.discord.api.message.ComponentMessageBuilder;
import com.bearify.discord.api.message.ContainerBuilder;
import com.bearify.discord.api.message.InteractiveButton;
import com.bearify.music.player.bridge.model.TrackMetadata;

import java.awt.Color;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.bearify.discord.api.message.ButtonStyle.SECONDARY;

public class PlaybackComponent {

    public static final String PLAYER_SHUFFLE_LABEL = "⤮";
    public static final String PLAYER_PREVIOUS_LABEL = "❙◀︎";
    public static final String PLAYER_PAUSE_LABEL = "❚❚";
    public static final String PLAYER_PLAY_LABEL = "▶︎";
    public static final String PLAYER_NEXT_LABEL = "▶︎❙";
    public static final String PLAYER_SHOW_QUEUE_LABEL = "Show";
    public static final String PLAYER_LOOP_OFF_LABEL = "↻";
    public static final String PLAYER_LOOP_QUEUE_LABEL = "↻";
    public static final String PLAYER_LOOP_TRACK_LABEL = "↻¹";
    private static final int VOLUME_METER_SEGMENTS = 10;
    private static final String ANSI_RESET = "\u001b[0m";
    // a title like "*NSYNC" or "||secret||" would otherwise format the rest of the message
    private static final Pattern MARKDOWN = Pattern.compile("[\\\\*_~|`>\\[\\]]");
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
                .text(notification.text())));
    }

    private static void appendHero(ComponentMessageBuilder builder, PlaybackComponentState state) {
        builder.container(container -> {
            container.text(tuner(state.playbackState(), state.volume()));
            state.artworkUri().ifPresent(uri -> container.image(uri.toString(), "Track artwork"));
            container.section(section -> {
                section.text("## " + escaped(truncate(state.track().title(), 45)));
                section.linkButton(state.track().uri(), "Source");
            });
            container.text("### " + trackMetaLine(state.track(), 40));
            container.separator();
            container.text("-# **Requested by** " + state.requesterTag());
        });
    }

    private static void appendActionRow(ComponentMessageBuilder builder, PlaybackComponentState state) {
        builder.container(container -> container.ActionRow(row -> {
            if (state.shuffle()) {
                row.primary("player:shuffle", PLAYER_SHUFFLE_LABEL);
            } else {
                row.secondary("player:shuffle", PLAYER_SHUFFLE_LABEL);
            }
            row.secondary("player:previous", PLAYER_PREVIOUS_LABEL);
            row.primary("player:pause-play", state.paused() ? PLAYER_PLAY_LABEL : PLAYER_PAUSE_LABEL);
            row.secondary("player:next", PLAYER_NEXT_LABEL);
            switch (state.loopMode()) {
                case OFF -> row.secondary("player:loop", PLAYER_LOOP_OFF_LABEL);
                case QUEUE -> row.primary("player:loop", PLAYER_LOOP_QUEUE_LABEL);
                case TRACK -> row.primary("player:loop", PLAYER_LOOP_TRACK_LABEL);
            }
        }));
    }

    /**
     * The status line as a radio's tuner faceplate, in an ansi code block (the only way Discord colours text): an
     * on-air light that is red while playing, a VU meter for the volume and, below full volume, its level.
     */
    static String tuner(PlaybackComponentState.PlaybackState playbackState, int volume) {
        String light = ansi(playbackState == PlaybackComponentState.PlaybackState.PLAYING ? "31" : "30", "●");
        // rounded up, so any volume you can hear lights at least one segment
        int lit = Math.ceilDiv(Math.clamp(volume, 0, 100), 100 / VOLUME_METER_SEGMENTS);
        StringBuilder meter = new StringBuilder();
        for (int segment = 1; segment <= VOLUME_METER_SEGMENTS; segment++) {
            String colour = segment == VOLUME_METER_SEGMENTS ? "31" : segment > VOLUME_METER_SEGMENTS - 3 ? "33" : "32";
            meter.append(segment <= lit ? ansi(colour, "▮") : ansi("30", "▯"));
        }
        String level = volume >= 100 ? "" : "  " + ansi("1;37", volume <= 0 ? "MUTE" : String.valueOf(volume));
        return "```ansi\n" + light + " " + ansi("1;37", "ON AIR") + "   " + meter + level + "\n```";
    }

    private static String ansi(String code, String text) {
        return "\u001b[" + code + "m" + text + ANSI_RESET;
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
                    escaped(truncate(first.title(), 50)),
                    trackMetaLine(first, 50)
            ));
            List<TrackMetadata> rest = state.upNext().subList(1, state.upNext().size());
            if (!rest.isEmpty()) {
                container.separator();
                appendTrackRows(container, rest);
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

    /** Lists tracks the way Up Next lists all but its first. */
    static void appendTrackRows(ContainerBuilder container, List<TrackMetadata> tracks) {
        for (int index = 0; index < tracks.size(); index++) {
            TrackMetadata track = tracks.get(index);
            container.text("""
                    %s
                    -# %s
                    """.formatted(
                    escaped(truncate(track.title(), 50)),
                    trackMetaLine(track, 50)
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

    private static String trackMetaLine(TrackMetadata track, int authorLimit) {
        return escaped(truncate(track.author(), authorLimit)) + " \u2022 " + length(track);
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

    static String escaped(String text) {
        return MARKDOWN.matcher(text).replaceAll("\\\\$0");
    }

    static String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        if (text.length() <= limit) {
            return text;
        }
        return TextCut.cut(text, limit - 1) + "\u2026";
    }

    static String humanReadable(Duration duration) {
        long totalSeconds = duration.toSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes >= 60) {
            return "%dh %02dm %02ds".formatted(minutes / 60, minutes % 60, seconds);
        }
        if (minutes > 0) {
            return "%dm %02ds".formatted(minutes, seconds);
        }
        return "%ds".formatted(seconds);
    }
}
