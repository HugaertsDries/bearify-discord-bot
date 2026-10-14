package com.bearify.controller.music.discord;

import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.controller.music.domain.TrackCatalogue;
import com.bearify.discord.api.interaction.Option;
import com.bearify.discord.api.interaction.AutocompleteInteraction;
import com.bearify.discord.spring.annotation.DiscordController;
import com.bearify.discord.spring.annotation.Interaction;
import com.bearify.discord.spring.annotation.InteractionGroup;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.TrackMetadata;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.bearify.discord.api.interaction.InteractionType.AUTOCOMPLETE;

@DiscordController
@InteractionGroup(value = "player", description = "Player commands")
public class MusicPlayerAutocompleteController {

    /** Splits a {@code /player remove} suggestion's value into its position and uri prefix. */
    static final String REMOVE_VALUE_SEPARATOR = "|";
    private static final int LIMIT = 5;
    private static final String SEPARATOR = " • ";
    private static final int MAX_LABEL_LENGTH = 100;
    private static final int MAX_VALUE_LENGTH = 100;
    private static final int MAX_CHOICES = 25;

    private static final List<Option> LOOP_MODES = List.of(
            new Option("Off", "off"),
            new Option("Queue: keep the whole queue going round", "queue"),
            new Option("Track: repeat the song that's playing", "track"));

    private final TrackCatalogue catalogue;
    private final MusicPlayerPool pool;

    public MusicPlayerAutocompleteController(TrackCatalogue catalogue, MusicPlayerPool pool) {
        this.catalogue = catalogue;
        this.pool = pool;
    }

    @SuppressWarnings("unused")
    @Interaction(type = AUTOCOMPLETE, value = "player:play:search")
    public void play(AutocompleteInteraction interaction) {
        String query = interaction.getValue().trim();
        String guildId = interaction.getGuildId().orElse("");
        if (query.isBlank() || guildId.isBlank() || PlayableMessage.isLink(query)) {
            interaction.reply(List.of());
            return;
        }

        List<Option> options = catalogue
                .find(guildId, query, LIMIT).stream()
                .filter(track -> track.uri() != null && !track.uri().isBlank())
                .map(track -> new Option(formatLabel(track), track.uri()))
                .toList();
        interaction.reply(options);
    }

    @SuppressWarnings("unused")
    @Interaction(type = AUTOCOMPLETE, value = "player:remove:track")
    public void remove(AutocompleteInteraction interaction) {
        String typed = interaction.getValue().trim().toLowerCase(Locale.ROOT);
        List<TrackMetadata> upcoming = interaction.getGuildId()
                .flatMap(guildId -> interaction.getVoiceChannelId().flatMap(voiceChannelId -> pool.find(guildId, voiceChannelId)))
                .flatMap(MusicPlayer::queue)
                .flatMap(reply -> reply instanceof MusicPlayerEvent.QueueSnapshot snapshot ? Optional.of(snapshot.upcoming()) : Optional.empty())
                .orElse(List.of());

        // the value carries the uri too: the queue can move while you type, and the bear then still finds the track you saw
        List<Option> options = IntStream.range(0, upcoming.size())
                .filter(index -> matches(upcoming.get(index), typed))
                .limit(MAX_CHOICES)
                .mapToObj(index -> {
                    TrackMetadata track = upcoming.get(index);
                    int position = index + 1;
                    return new Option(
                            PlaybackComponent.truncate(position + ". " + describe(track), MAX_LABEL_LENGTH),
                            cut(position + REMOVE_VALUE_SEPARATOR + Objects.requireNonNullElse(track.uri(), ""), MAX_VALUE_LENGTH));
                })
                .toList();
        interaction.reply(options);
    }

    @SuppressWarnings("unused")
    @Interaction(type = AUTOCOMPLETE, value = "player:loop:mode")
    public void loop(AutocompleteInteraction interaction) {
        String typed = interaction.getValue().trim().toLowerCase(Locale.ROOT);
        interaction.reply(LOOP_MODES.stream()
                .filter(option -> option.name().toLowerCase(Locale.ROOT).contains(typed))
                .toList());
    }

    // a bear may not know a track's title or author, and Discord would otherwise show "null"
    private static String describe(TrackMetadata track) {
        return Stream.of(track.title(), track.author())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(SEPARATOR));
    }

    private static boolean matches(TrackMetadata track, String typed) {
        return typed.isEmpty()
                || (track.title() != null && track.title().toLowerCase(Locale.ROOT).contains(typed))
                || (track.author() != null && track.author().toLowerCase(Locale.ROOT).contains(typed));
    }

    // no ellipsis: the bear matches the uri by this prefix
    private static String cut(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private static String formatLabel(TrackMetadata track) {
        String suffix = SEPARATOR + PlaybackComponent.length(track);
        int maxTitleLength = Math.max(0, MAX_LABEL_LENGTH - suffix.length());
        String title = track.title() == null ? "" : track.title();
        if (title.length() > maxTitleLength) {
            title = title.substring(0, maxTitleLength);
        }
        return title + suffix;
    }
}
