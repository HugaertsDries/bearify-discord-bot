package com.bearify.music.player.agent.domain.redis;

import com.bearify.music.player.agent.domain.InteractionHandler;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.protocol.PlayerMessageCodec;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

class RedisInteractionSubscription implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(RedisInteractionSubscription.class);

    private final RedisConnectionFactory connectionFactory;
    private final PlayerMessageCodec codec;
    private final InteractionHandler interactionHandler;
    private final String playerId;
    private RedisMessageListenerContainer container;

    RedisInteractionSubscription(
            RedisConnectionFactory connectionFactory,
            PlayerMessageCodec codec,
            InteractionHandler interactionHandler,
            String playerId) {
        this.connectionFactory = connectionFactory;
        this.codec = codec;
        this.interactionHandler = interactionHandler;
        this.playerId = playerId;
    }

    @Override
    public void start() {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(onMessage(), new ChannelTopic(PlayerRedisProtocol.Channels.commands(playerId)));
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
                MusicPlayerInteraction interaction = codec.parseInteraction(message.getBody());
                interactionHandler.handle(interaction);
            } catch (Exception e) {
                LOG.error("Failed to handle player interaction", e);
            }
        };
    }
}
