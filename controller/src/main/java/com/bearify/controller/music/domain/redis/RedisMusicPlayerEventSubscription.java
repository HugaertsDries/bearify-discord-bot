package com.bearify.controller.music.domain.redis;

import com.bearify.controller.music.domain.MusicPlayerEventRouter;
import com.bearify.shared.events.PlayerEvent;
import com.bearify.shared.player.PlayerMessageCodec;
import com.bearify.shared.player.PlayerRedisProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import static com.bearify.shared.player.PlayerRedisProtocol.EVENTS_CHANNEL;

/**
 * Subscribes to the shared Redis player-events channel for the lifetime of the Spring application
 * and forwards decoded events into the controller's in-process router.
 */
@Component
class RedisMusicPlayerEventSubscription implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(RedisMusicPlayerEventSubscription.class);

    private final RedisConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;
    private final MusicPlayerEventRouter eventRouter;
    private RedisMessageListenerContainer container;

    RedisMusicPlayerEventSubscription(RedisConnectionFactory connectionFactory,
                                      ObjectMapper objectMapper,
                                      MusicPlayerEventRouter eventRouter) {
        this.connectionFactory = connectionFactory;
        this.objectMapper = objectMapper;
        this.eventRouter = eventRouter;
    }

    @Override
    public void start() {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(onMessage(), new ChannelTopic(EVENTS_CHANNEL));
        container.afterPropertiesSet();
        container.start();
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }

    @Override
    public boolean isRunning() {
        return container != null && container.isRunning();
    }

    private MessageListener onMessage() {
        return (message1, pattern) -> {
            try {
                PlayerEvent event = PlayerMessageCodec.readEvent(objectMapper, message1.getBody());
                eventRouter.route(event);
            } catch (Exception e) {
                LOG.error("Failed to handle player event", e);
            }
        };
    }
}
