package com.bearify.music.player.agent.redis;

import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.StringRedisTemplate;

public class MusicPlayerRegistration implements SmartLifecycle {

    private final StringRedisTemplate redis;
    private final String playerId;
    private volatile boolean running = false;

    MusicPlayerRegistration(StringRedisTemplate redis, String playerId) {
        this.redis = redis;
        this.playerId = playerId;
    }

    @Override
    public void start() {
        redis.opsForSet().add(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS, playerId);
        running = true;
    }

    @Override
    public void stop() {
        redis.opsForSet().remove(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS, playerId);
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
