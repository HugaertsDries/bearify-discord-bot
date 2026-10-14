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

import java.util.concurrent.Executors;

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
        // one thread, so a server's interactions run in the order they were sent; no handler blocks, slow loads run on LavaPlayer's threads
        container.setTaskExecutor(Executors.newSingleThreadExecutor(Thread.ofPlatform().name("player-interactions").daemon().factory()));
        container.addMessageListener(onInteraction(objectMapper, dispatcher), new ChannelTopic(PlayerRedisProtocol.Channels.interactions(playerId)));
        // shortcut: every agent answers a search, route to one agent if the agent count grows
        container.addMessageListener(onInteraction(objectMapper, dispatcher), new ChannelTopic(PlayerRedisProtocol.Channels.SEARCH));
        container.addMessageListener(onInteraction(objectMapper, dispatcher), new ChannelTopic(PlayerRedisProtocol.Channels.HEALTH));
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
