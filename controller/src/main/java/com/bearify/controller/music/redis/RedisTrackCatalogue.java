package com.bearify.controller.music.redis;

import com.bearify.controller.music.domain.MusicPlayerPendingInteractions;
import com.bearify.controller.music.domain.TrackCatalogue;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.model.TrackMetadata;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

class RedisTrackCatalogue implements TrackCatalogue {

    private final RedisBroadcast broadcast;
    private final MusicPlayerPoolProperties properties;

    RedisTrackCatalogue(StringRedisTemplate redis,
                        ObjectMapper objectMapper,
                        MusicPlayerPendingInteractions pendingInteractions,
                        MusicPlayerPoolProperties properties) {
        this.broadcast = new RedisBroadcast(redis, objectMapper, pendingInteractions);
        this.properties = properties;
    }

    @Override
    public List<TrackMetadata> find(String guildId, String query, int limit) {
        return broadcast.request(
                        PlayerRedisProtocol.Channels.SEARCH,
                        requestId -> new MusicPlayerInteraction.Search(requestId, guildId, query, limit),
                        MusicPlayerEvent.SearchResults.class,
                        properties.searchTimeout())
                .map(MusicPlayerEvent.SearchResults::tracks)
                .orElse(List.of());
    }
}
