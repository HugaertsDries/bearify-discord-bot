package com.bearify.music.player.agent.domain.redis;

import com.bearify.music.player.agent.domain.Registrar;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisRegistrar implements Registrar, SmartLifecycle {

    private final StringRedisTemplate redis;
    private final String playerId;
    private volatile boolean running = false;

    RedisRegistrar(StringRedisTemplate redis, String playerId) {
        this.redis = redis;
        this.playerId = playerId;
    }

    @Override
    public void register() {
        redis.opsForSet().add(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS, playerId);
    }

    @Override
    public void deregister() {
        redis.opsForSet().remove(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS, playerId);
    }

    @Override
    public void start() {
        register();
        running = true;
    }

    @Override
    public void stop() {
        deregister();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
