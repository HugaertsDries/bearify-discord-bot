package com.bearify.music.player.agent.domain.redis;

import com.bearify.music.player.agent.AbstractAgentIntegrationTest;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.protocol.PlayerMessageCodec;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RedisMusicEventPublisherIntegrationTest extends AbstractAgentIntegrationTest {

    @Autowired
    RedisEventPublisher publisher;
    @Autowired RedisConnectionFactory connectionFactory;
    @Autowired PlayerMessageCodec codec;

    private RedisMessageListenerContainer container;

    @AfterEach
    void stopContainer() {
        if (container != null) {
            container.stop();
        }
    }

    // --- HAPPY PATH ---

    @Test
    void publishesEncodedEventToSharedEventsChannel() throws Exception {
        AtomicReference<MusicPlayerEvent> received = new AtomicReference<>();
        startListener(body -> received.set(codec.parseEvent(body)));

        MusicPlayerEvent event = new MusicPlayerEvent.Ready("player-1", "req-1");
        publisher.publish(event);

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(received.get()).isEqualTo(event));
    }

    private void startListener(MessageHandler handler) throws Exception {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                (message, pattern) -> handler.handle(message.getBody()),
                new ChannelTopic(PlayerRedisProtocol.Channels.EVENTS));
        container.afterPropertiesSet();
        container.start();
    }

    @FunctionalInterface
    private interface MessageHandler {
        void handle(byte[] body);
    }
}
