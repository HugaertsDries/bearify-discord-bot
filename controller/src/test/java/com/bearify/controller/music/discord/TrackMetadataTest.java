package com.bearify.controller.music.discord;

import com.bearify.music.player.bridge.model.TrackMetadata;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class TrackMetadataTest {

    @Test
    void constructorExposesArtworkAsOptionalWithoutOptionalParameter() {
        TrackMetadata track = new TrackMetadata("Song", "Artist", "https://example.com", 60_000, "https://cdn.example/art.png");

        assertThat(track.artworkUrl()).contains("https://cdn.example/art.png");
    }

    @Test
    void constructorDefaultsMissingArtworkToEmptyOptional() {
        TrackMetadata track = new TrackMetadata("Song", "Artist", "https://example.com", 60_000, null);

        assertThat(track.artworkUrl()).isEmpty();
    }

    @Test
    void jsonRoundTripKeepsNullableArtworkFieldShape() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        TrackMetadata original = new TrackMetadata("Song", "Artist", "https://example.com", 60_000, "https://cdn.example/art.png");

        String json = objectMapper.writeValueAsString(original);
        TrackMetadata restored = objectMapper.readValue(json, TrackMetadata.class);

        assertThat(json).contains("\"artworkUrl\":\"https://cdn.example/art.png\"");
        assertThat(restored.artworkUrl()).contains("https://cdn.example/art.png");
    }
}
