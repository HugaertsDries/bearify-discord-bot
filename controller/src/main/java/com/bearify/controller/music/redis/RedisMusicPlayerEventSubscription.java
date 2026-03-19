package com.bearify.controller.music.redis;

import com.bearify.controller.music.port.MusicPlayerEventRouter;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Subscribes to the shared Redis player-events channel for the lifetime of the Spring application
 * and forwards decoded events into the controller's in-process router.
 */
class RedisMusicPlayerEventSubscription implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(RedisMusicPlayerEventSubscription.class);

    private final RedisMessageListenerContainer container;

    RedisMusicPlayerEventSubscription(RedisConnectionFactory connectionFactory,
                                      ObjectMapper objectMapper,
                                      MusicPlayerEventRouter eventRouter) {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(onMessage(objectMapper, eventRouter), new ChannelTopic(PlayerRedisProtocol.Channels.EVENTS));
        container.afterPropertiesSet();
    }

    @Override
    public void start() {
        container.start();
    }

    @Override
    public void stop() {
        container.stop();
    }

    @Override
    public boolean isRunning() {
        return container.isRunning();
    }

    private static MessageListener onMessage(ObjectMapper objectMapper, MusicPlayerEventRouter eventRouter) {
        return (message, pattern) -> {
            try {
                MusicPlayerEvent event = objectMapper.readValue(message.getBody(), MusicPlayerEvent.class);
                eventRouter.route(event);
            } catch (Exception e) {
                LOG.error("Failed to handle player event", e);
            }
        };
    }
}
