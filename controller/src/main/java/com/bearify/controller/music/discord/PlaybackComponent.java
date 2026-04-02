package com.bearify.controller.music.discord;

import com.bearify.discord.api.message.ComponentMessage;
import com.bearify.discord.api.message.ComponentMessageBuilder;
import com.bearify.discord.api.message.InteractiveButton;
import com.bearify.music.player.bridge.model.TrackMetadata;

import java.awt.Color;
import java.time.Duration;

import static com.bearify.discord.api.message.ButtonStyle.SECONDARY;

public class PlaybackComponent {

    private static final AnnouncerProperties DEFAULT_PROPERTIES = new AnnouncerProperties(
            "#FDB529",
            "#CD4631",
            "Bearify \u2022 Powered by Bearable Software",
            Duration.ofSeconds(15)
    );
    public static final String PLAYER_PREVIOUS_LABEL = "❙◀︎◀︎";
    public static final String PLAYER_REWIND_LABEL = "◀︎";
    public static final String PLAYER_PAUSE_LABEL = "❚❚";
    public static final String PLAYER_PLAY_LABEL = "▶︎";
    public static final String PLAYER_FORWARD_LABEL = "▶︎";
    public static final String PLAYER_NEXT_LABEL = "▶︎▶︎❙";

    private final AnnouncerProperties properties;

    public PlaybackComponent() {
        this(DEFAULT_PROPERTIES);
    }

    public PlaybackComponent(AnnouncerProperties properties) {
        this.properties = properties;
    }

    public ComponentMessage render(PlaybackComponentState state) {
        ComponentMessageBuilder builder = ComponentMessage.builder("playback-announcer");
        appendNotification(builder, state);
        appendHero(builder, state);
        appendActionRow(builder, state);
        appendQueue(builder, state);
        builder.text("-# " + state.footerText());
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
            container.text("### " + trackMetaLine(state.track(), 40));
            container.separator();
            container.text("-# **Requested by** " + state.requesterTag());
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
            TrackMetadata first = state.upNext().getFirst();
            container.text("""
                    **%s**
                    -# **%s**

                    """.formatted(
                    truncate(first.title(), 50),
                    trackMetaLine(first, 50)
            ));
            if (state.upNext().size() > 1) {
                container.separator();
            }
            for (int index = 1; index < state.upNext().size(); index++) {
                TrackMetadata track = state.upNext().get(index);
                container.text("""
                        %s
                        -# %s
                        """.formatted(
                        truncate(track.title(), 50),
                        trackMetaLine(track, 50)
                ));
                if (index + 1 < state.upNext().size()) {
                    container.separator();
                }
            }
        });
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
        return truncate(track.author(), authorLimit) + " \u2022 " + humanReadable(Duration.ofMillis(track.durationMs()));
    }

    private static String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        return text.length() <= limit ? text : text.substring(0, limit - 1) + "\u2026";
    }

    private static String humanReadable(Duration duration) {
        return duration.toString()
                .substring(2)
                .replaceAll("(\\d[HMS])(?!$)", "$1 ")
                .toLowerCase();
    }
}
