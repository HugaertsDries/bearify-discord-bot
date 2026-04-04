package com.bearify.controller.music.discord;

import com.bearify.controller.music.domain.TrackCatalogue;
import com.bearify.discord.api.interaction.Option;
import com.bearify.discord.testing.MockAutocompleteInteraction;
import com.bearify.music.player.bridge.model.TrackMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MusicPlayerAutocompleteControllerTest {

    @Test
    void returnsNoChoicesForUrlInput() {
        TrackCatalogue trackCatalogue = mock(TrackCatalogue.class);
        MusicPlayerAutocompleteController controller = new MusicPlayerAutocompleteController(trackCatalogue);
        MockAutocompleteInteraction interaction = MockAutocompleteInteraction.forAutocomplete(
                        "player:play:search",
                        "https://youtube.com/watch?v=abc")
                .guildId("guild-456")
                .build();

        controller.play(interaction);

        assertThat(interaction.getReplies()).containsExactly(List.of());
    }

    @Test
    void formatsChoicesFromTrackMetadata() {
        TrackCatalogue trackCatalogue = mock(TrackCatalogue.class);
        when(trackCatalogue.find("guild-456", "daft punk", 5)).thenReturn(List.of(
                new TrackMetadata("One More Time", "Daft Punk", "https://youtube.com/watch?v=1", 320_000)
        ));
        MusicPlayerAutocompleteController controller = new MusicPlayerAutocompleteController(trackCatalogue);
        MockAutocompleteInteraction interaction = MockAutocompleteInteraction.forAutocomplete("player:play:search", "daft punk")
                .guildId("guild-456")
                .build();

        controller.play(interaction);

        assertThat(interaction.getReplies()).containsExactly(List.of(
                new Option("One More Time • 5m 20s", "https://youtube.com/watch?v=1")));
    }

    @Test
    void truncatesLongTitlesToKeepChoiceNameWithinJdaLimit() {
        String longTitle = "A Cup of Liber-tea (Helldivers 2 Main Theme) | Helldivers 2 (Original Game Soundtrack)";
        TrackCatalogue trackCatalogue = mock(TrackCatalogue.class);
        when(trackCatalogue.find("guild-456", "helldivers", 5)).thenReturn(List.of(
                new TrackMetadata(longTitle, "SonySoundtracksVEVO", "https://youtube.com/watch?v=2", 223_000)
        ));
        MusicPlayerAutocompleteController controller = new MusicPlayerAutocompleteController(trackCatalogue);
        MockAutocompleteInteraction interaction = MockAutocompleteInteraction.forAutocomplete("player:play:search", "helldivers")
                .guildId("guild-456")
                .build();

        controller.play(interaction);

        List<Option> reply = interaction.getReplies().getFirst();
        assertThat(reply).hasSize(1);
        assertThat(reply.getFirst().name()).endsWith("• 3m 43s");
        assertThat(reply.getFirst().name()).doesNotContain("SonySoundtracksVEVO");
        assertThat(reply.getFirst().name().length()).isLessThanOrEqualTo(100);
    }
}
