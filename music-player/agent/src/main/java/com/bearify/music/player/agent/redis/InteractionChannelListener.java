package com.bearify.music.player.agent.redis;

import com.bearify.music.player.agent.port.MusicPlayerInteractionDispatcher;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

class InteractionChannelListener implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(InteractionChannelListener.class);

    private final RedisMessageListenerContainer container;

    InteractionChannelListener(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper,
            MusicPlayerInteractionDispatcher dispatcher,
            String playerId) {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(onInteraction(objectMapper, dispatcher), new ChannelTopic(PlayerRedisProtocol.Channels.interactions(playerId)));
        container.addMessageListener(onInteraction(objectMapper, dispatcher), new ChannelTopic(PlayerRedisProtocol.Channels.SEARCH));
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

    private static MessageListener onInteraction(ObjectMapper objectMapper, MusicPlayerInteractionDispatcher dispatcher) {
        return (message, pattern) -> {
            try {
                MusicPlayerInteraction interaction = objectMapper.readValue(message.getBody(), MusicPlayerInteraction.class);
                dispatcher.handle(interaction);
            } catch (Exception e) {
                LOG.error("Failed to handle player interaction", e);
            }
        };
    }
}
