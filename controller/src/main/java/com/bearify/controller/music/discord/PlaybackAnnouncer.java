package com.bearify.controller.music.discord;

import com.bearify.discord.api.message.ComponentMessage;
import com.bearify.discord.api.message.LinkButton;
import com.bearify.music.player.bridge.model.TrackMetadata;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class PlaybackAnnouncer {

    public ComponentMessage render(PlaybackAnnouncerState state) {
        var builder = ComponentMessage.builder("playback-announcer");
        builder.container(container -> state.notification().ifPresent(text -> container.accentColor(16626985).text("\uD83D\uDD14 " + text)));
        builder.container(container -> {
                    container.text("-# **" + state.playbackState().header() + "**");
                    state.artworkUri().ifPresent(uri -> container.image(uri.toString(), "Track artwork"));
                    container.section(section -> {
                        section.text("## " + truncate(state.track().title(), 45));
                        section.accessory(LinkButton.of(state.track().uri(), "Source"));
                    });
                    container.text("### " + truncate(state.track().author(), 40) + " • " + humanReadable(Duration.ofMillis(state.track().durationMs())));
                    container.text("-# **Requested by** " + state.requesterTag());
                });
        builder.container(container -> container.ActionRow(row -> {
            row.secondary("player:previous", "❙◀︎◀︎");
            row.secondary("player:rewind", "◀︎");
            row.primary("player:pause-play", state.paused() ? "▶︎" : "❚❚");
            row.secondary("player:forward", "▶︎");
            row.secondary("player:next", "▶︎▶︎❙");
        }));
        if (!state.upNext().isEmpty()) {
            builder.container(container -> {
                container.text("**Up Next**");
                container.text(String.join("\n", formatUpNext(state.upNext())));
            });
        }
        builder.text("-# " + state.footerText());
        return builder.build();
    }

    private static List<String> formatUpNext(List<TrackMetadata> tracks) {
        List<String> formatted = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            TrackMetadata track = tracks.get(i);
            formatted.add((i + 1) + ". " + truncate(track.title(), 35));
        }
        return formatted;
    }

    private static String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        return text.length() <= limit ? text : text.substring(0, limit - 1) + "…";
    }

    private static String humanReadable(Duration duration) {
        return duration.toString()
                .substring(2)
                .replaceAll("(\\d[HMS])(?!$)", "$1 ")
                .toLowerCase();
    }
}
