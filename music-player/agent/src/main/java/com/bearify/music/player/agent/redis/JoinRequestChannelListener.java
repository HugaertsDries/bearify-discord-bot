package com.bearify.music.player.agent.redis;

import com.bearify.music.player.agent.domain.VoiceConnectionManager;
import com.bearify.music.player.bridge.events.JoinRequest;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

class JoinRequestChannelListener implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(JoinRequestChannelListener.class);

    private final RedisMessageListenerContainer container;

    JoinRequestChannelListener(RedisConnectionFactory connectionFactory,
                               ObjectMapper objectMapper,
                               VoiceConnectionManager voiceConnectionManager) {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                onRequest(objectMapper, voiceConnectionManager),
                new ChannelTopic(PlayerRedisProtocol.Channels.REQUESTS));
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

    private static MessageListener onRequest(ObjectMapper objectMapper, VoiceConnectionManager manager) {
        return (message, pattern) -> {
            try {
                JoinRequest request = objectMapper.readValue(message.getBody(), JoinRequest.class);
                manager.claim(request);
            } catch (Exception e) {
                LOG.error("Failed to handle connect request", e);
            }
        };
    }
}
