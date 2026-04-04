package com.bearify.controller.music.discord;

import com.bearify.controller.music.domain.TrackCatalogue;
import com.bearify.discord.api.interaction.Option;
import com.bearify.discord.api.interaction.AutocompleteInteraction;
import com.bearify.discord.spring.annotation.DiscordController;
import com.bearify.discord.spring.annotation.Interaction;
import com.bearify.discord.spring.annotation.InteractionGroup;
import com.bearify.music.player.bridge.model.TrackMetadata;

import java.time.Duration;
import java.util.List;

import static com.bearify.discord.api.interaction.InteractionType.AUTOCOMPLETE;

@DiscordController
@InteractionGroup(value = "player", description = "Player commands")
public class MusicPlayerAutocompleteController {

    private static final int LIMIT = 5;
    private static final String SEPARATOR = " • ";
    private static final int MAX_LABEL_LENGTH = 100;

    private final TrackCatalogue catalogue;

    public MusicPlayerAutocompleteController(TrackCatalogue catalogue) {
        this.catalogue = catalogue;
    }

    @SuppressWarnings("unused")
    @Interaction(type = AUTOCOMPLETE, value = "player:play:search")
    public void play(AutocompleteInteraction interaction) {
        String query = interaction.getValue().trim();
        String guildId = interaction.getGuildId().orElse("");
        if (query.isBlank() || guildId.isBlank() || isUrl(query)) {
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

    private static boolean isUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static String formatLabel(TrackMetadata track) {
        String duration = PlaybackComponent.humanReadable(Duration.ofMillis(track.durationMs()));
        String suffix = SEPARATOR + duration;
        int maxTitleLength = Math.max(0, MAX_LABEL_LENGTH - suffix.length());
        String title = track.title() == null ? "" : track.title();
        if (title.length() > maxTitleLength) {
            title = title.substring(0, maxTitleLength);
        }
        return title + suffix;
    }
}
