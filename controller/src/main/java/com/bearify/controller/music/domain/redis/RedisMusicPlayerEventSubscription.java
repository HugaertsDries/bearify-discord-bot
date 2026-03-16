package com.bearify.controller.music.domain.redis;

import com.bearify.controller.music.domain.MusicPlayerEventRouter;
import com.bearify.shared.events.MusicPlayerEvent;
import com.bearify.shared.player.PlayerMessageCodec;
import com.bearify.shared.player.PlayerRedisProtocol;
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

    private final RedisConnectionFactory connectionFactory;
    private final PlayerMessageCodec codec;
    private final MusicPlayerEventRouter eventRouter;
    private RedisMessageListenerContainer container;

    RedisMusicPlayerEventSubscription(RedisConnectionFactory connectionFactory,
                                      PlayerMessageCodec codec,
                                      MusicPlayerEventRouter eventRouter) {
        this.connectionFactory = connectionFactory;
        this.codec = codec;
        this.eventRouter = eventRouter;
    }

    @Override
    public void start() {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(onMessage(), new ChannelTopic(PlayerRedisProtocol.Channels.EVENTS));
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
        return (message, pattern) -> {
            try {
                MusicPlayerEvent event = codec.parseEvent(message.getBody());
                eventRouter.route(event);
            } catch (Exception e) {
                LOG.error("Failed to handle player event", e);
            }
        };
    }
}
