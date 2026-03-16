package com.bearify.music.player.agent.domain.redis;

import com.bearify.music.player.agent.domain.EventPublisher;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.protocol.PlayerMessageCodec;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisEventPublisher implements EventPublisher {

    private final StringRedisTemplate redis;
    private final PlayerMessageCodec codec;

    RedisEventPublisher(StringRedisTemplate redis, PlayerMessageCodec codec) {
        this.redis = redis;
        this.codec = codec;
    }

    @Override
    public void publish(MusicPlayerEvent event) {
        redis.convertAndSend(PlayerRedisProtocol.Channels.EVENTS, codec.serialize(event));
    }
}
