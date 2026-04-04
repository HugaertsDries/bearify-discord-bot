package com.bearify.controller.music.redis;

import com.bearify.controller.music.domain.MusicPlayerPendingInteractions;
import com.bearify.controller.music.domain.TrackCatalogue;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.model.TrackMetadata;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

class RedisTrackCatalogue implements TrackCatalogue {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MusicPlayerPendingInteractions pendingInteractions;
    private final MusicPlayerPoolProperties properties;

    RedisTrackCatalogue(StringRedisTemplate redis,
                        ObjectMapper objectMapper,
                        MusicPlayerPendingInteractions pendingInteractions,
                        MusicPlayerPoolProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.pendingInteractions = pendingInteractions;
        this.properties = properties;
    }

    @Override
    public List<TrackMetadata> find(String guildId, String query, int limit) {
        MusicPlayerPendingInteractions.PendingInteraction pending = pendingInteractions.register();
        redis.convertAndSend(
                PlayerRedisProtocol.Channels.SEARCH,
                serialize(new MusicPlayerInteraction.Search(pending.requestId(), guildId, query, limit)));
        return pending.future()
                .orTimeout(properties.interactionTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .thenApply(event -> event instanceof MusicPlayerEvent.SearchResults results ? results.tracks() : List.<TrackMetadata>of())
                .exceptionally(ex -> List.<TrackMetadata>of())
                .join();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize interaction", e);
        }
    }
}
