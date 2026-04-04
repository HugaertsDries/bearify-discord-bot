package com.bearify.controller.music.redis;

import com.bearify.controller.music.domain.MusicPlayerPendingInteractions;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.model.TrackMetadata;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class RedisTrackCatalogueTest {

    private static final String PLAYER_ID = "player-1";
    private static final String GUILD_ID = "guild-456";

    @Test
    void dispatchesSearchInteractionOnSharedChannelAndReturnsSearchResults() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        MusicPlayerPendingInteractions pendingInteractions = new MusicPlayerPendingInteractions();

        doAnswer(invocation -> {
            assertThat(invocation.getArgument(0, String.class)).isEqualTo(PlayerRedisProtocol.Channels.SEARCH);

            MusicPlayerInteraction.Search search = (MusicPlayerInteraction.Search) objectMapper.readValue(
                    invocation.getArgument(1, String.class),
                    MusicPlayerInteraction.class);
            assertThat(search.playerId()).isBlank();
            pendingInteractions.accept(new MusicPlayerEvent.SearchResults(
                    PLAYER_ID,
                    search.requestId(),
                    GUILD_ID,
                    List.of(new TrackMetadata("One More Time", "Daft Punk", "https://youtube.com/watch?v=1", 320_000))
            ));
            return null;
        }).when(redis).convertAndSend(anyString(), anyString());

        RedisTrackCatalogue trackCatalogue = new RedisTrackCatalogue(
                redis,
                objectMapper,
                pendingInteractions,
                new MusicPlayerPoolProperties(Duration.ofSeconds(5), Duration.ofSeconds(5)));

        List<TrackMetadata> results = trackCatalogue.find(GUILD_ID, "daft punk", 5);

        assertThat(results).containsExactly(
                new TrackMetadata("One More Time", "Daft Punk", "https://youtube.com/watch?v=1", 320_000));
    }
}
