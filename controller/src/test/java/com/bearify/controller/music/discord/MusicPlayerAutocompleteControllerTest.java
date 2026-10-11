package com.bearify.controller.music.discord;

import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.controller.music.domain.TrackCatalogue;
import com.bearify.discord.api.interaction.Option;
import com.bearify.discord.testing.MockAutocompleteInteraction;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.TrackMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MusicPlayerAutocompleteControllerTest {

    private static final String GUILD_ID = "guild-456";
    private static final String VOICE_CHANNEL_ID = "vc-123";
    private static final String REMOVE_ID = "player:remove:track";
    private static final TrackMetadata AFRICA = new TrackMetadata("Africa", "TOTO", "https://example.com/africa", 295_000);
    private static final TrackMetadata HOLD_THE_LINE = new TrackMetadata("Hold the Line", "TOTO", "https://example.com/hold", 236_000);
    private static final TrackMetadata TAKE_ON_ME = new TrackMetadata("Take On Me", "a-ha", "https://example.com/take", 225_000);

    @Test
    void listsQueuedTracksFilteredByTypedText() {
        MusicPlayerAutocompleteController controller = removeController(snapshot(List.of(TAKE_ON_ME, AFRICA, HOLD_THE_LINE)));
        MockAutocompleteInteraction interaction = removeInteraction("toto");

        controller.remove(interaction);

        assertThat(interaction.getReplies()).containsExactly(List.of(
                new Option("2. Africa — TOTO", "2|https://example.com/africa"),
                new Option("3. Hold the Line — TOTO", "3|https://example.com/hold")));
    }

    @Test
    void capsSuggestionsAtTwentyFive() {
        List<TrackMetadata> queue = IntStream.rangeClosed(1, 30)
                .mapToObj(i -> new TrackMetadata("Song " + i, "Artist", "https://example.com/" + i, 60_000))
                .toList();
        MusicPlayerAutocompleteController controller = removeController(snapshot(queue));
        MockAutocompleteInteraction interaction = removeInteraction("");

        controller.remove(interaction);

        assertThat(interaction.getReplies().getFirst()).hasSize(25);
    }

    @Test
    void encodesPositionAndUriWithinHundredChars() {
        String longUri = "https://soundcloud.com/" + "a".repeat(150);
        TrackMetadata longTrack = new TrackMetadata("L".repeat(150), "Artist", longUri, 60_000);
        MusicPlayerAutocompleteController controller = removeController(snapshot(List.of(longTrack)));
        MockAutocompleteInteraction interaction = removeInteraction("");

        controller.remove(interaction);

        Option option = interaction.getReplies().getFirst().getFirst();
        assertThat(option.name()).hasSize(100).startsWith("1. LLL");
        assertThat(option.value()).hasSize(100).isEqualTo(("1|" + longUri).substring(0, 100));
    }

    @Test
    void returnsNothingWhenNoBearInChannel() {
        MusicPlayerPool pool = mock(MusicPlayerPool.class);
        when(pool.find(GUILD_ID, VOICE_CHANNEL_ID)).thenReturn(Optional.empty());
        MusicPlayerAutocompleteController controller = new MusicPlayerAutocompleteController(mock(TrackCatalogue.class), pool);
        MockAutocompleteInteraction interaction = removeInteraction("africa");

        controller.remove(interaction);

        assertThat(interaction.getReplies()).containsExactly(List.of());
    }

    @Test
    void returnsNothingWhenBearIsSilent() {
        MusicPlayerAutocompleteController controller = removeController(Optional.empty());
        MockAutocompleteInteraction interaction = removeInteraction("africa");

        controller.remove(interaction);

        assertThat(interaction.getReplies()).containsExactly(List.of());
    }

    @Test
    void returnsNoChoicesForUrlInput() {
        TrackCatalogue trackCatalogue = mock(TrackCatalogue.class);
        MusicPlayerAutocompleteController controller = new MusicPlayerAutocompleteController(trackCatalogue, mock(MusicPlayerPool.class));
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
        MusicPlayerAutocompleteController controller = new MusicPlayerAutocompleteController(trackCatalogue, mock(MusicPlayerPool.class));
        MockAutocompleteInteraction interaction = MockAutocompleteInteraction.forAutocomplete("player:play:search", "daft punk")
                .guildId("guild-456")
                .build();

        controller.play(interaction);

        assertThat(interaction.getReplies()).containsExactly(List.of(
                new Option("One More Time • 5m 20s", "https://youtube.com/watch?v=1")));
    }

    @Test
    void labelsLiveResultsAsLive() {
        TrackCatalogue trackCatalogue = mock(TrackCatalogue.class);
        when(trackCatalogue.find("guild-456", "lofi radio", 5)).thenReturn(List.of(
                new TrackMetadata("Lofi Radio", "Lofi Girl", "https://youtube.com/watch?v=3", Long.MAX_VALUE)
        ));
        MusicPlayerAutocompleteController controller = new MusicPlayerAutocompleteController(trackCatalogue, mock(MusicPlayerPool.class));
        MockAutocompleteInteraction interaction = MockAutocompleteInteraction.forAutocomplete("player:play:search", "lofi radio")
                .guildId("guild-456")
                .build();

        controller.play(interaction);

        assertThat(interaction.getReplies()).containsExactly(List.of(
                new Option("Lofi Radio • 🔴 LIVE", "https://youtube.com/watch?v=3")));
    }

    @Test
    void truncatesLongTitlesToKeepChoiceNameWithinJdaLimit() {
        String longTitle = "A Cup of Liber-tea (Helldivers 2 Main Theme) | Helldivers 2 (Original Game Soundtrack)";
        TrackCatalogue trackCatalogue = mock(TrackCatalogue.class);
        when(trackCatalogue.find("guild-456", "helldivers", 5)).thenReturn(List.of(
                new TrackMetadata(longTitle, "SonySoundtracksVEVO", "https://youtube.com/watch?v=2", 223_000)
        ));
        MusicPlayerAutocompleteController controller = new MusicPlayerAutocompleteController(trackCatalogue, mock(MusicPlayerPool.class));
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

    private static MusicPlayerAutocompleteController removeController(Optional<MusicPlayerEvent> queueReply) {
        MusicPlayer player = mock(MusicPlayer.class);
        when(player.queue()).thenReturn(queueReply);
        MusicPlayerPool pool = mock(MusicPlayerPool.class);
        when(pool.find(GUILD_ID, VOICE_CHANNEL_ID)).thenReturn(Optional.of(player));
        return new MusicPlayerAutocompleteController(mock(TrackCatalogue.class), pool);
    }

    private static Optional<MusicPlayerEvent> snapshot(List<TrackMetadata> upcoming) {
        return Optional.of(new MusicPlayerEvent.QueueSnapshot("player-1", "req-1", GUILD_ID, Optional.empty(), upcoming));
    }

    private static MockAutocompleteInteraction removeInteraction(String typed) {
        return MockAutocompleteInteraction.forAutocomplete(REMOVE_ID, typed)
                .guildId(GUILD_ID)
                .voiceChannelId(VOICE_CHANNEL_ID)
                .build();
    }
}
