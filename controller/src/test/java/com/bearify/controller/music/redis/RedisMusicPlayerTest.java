package com.bearify.controller.music.redis;

import com.bearify.controller.AbstractControllerIntegrationTest;
import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerJoinResultHandler;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RedisMusicPlayerTest extends AbstractControllerIntegrationTest {

    private static final String PLAYER_ID = "player-1";
    private static final String GUILD_ID = "guild-1";
    private static final String VOICE_CHANNEL_ID = "voice-1";

    @Autowired MusicPlayerPool pool;
    @Autowired RedisConnectionFactory redisConnectionFactory;
    @Autowired ObjectMapper objectMapper;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void seedPlayer() {
        redis.delete(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS);
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        redis.opsForSet().add(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS, PLAYER_ID);
    }

    // --- HAPPY PATH ---

    @Test
    void joinPublishesConnectToCorrectRedisChannel() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(
                (message, pattern) -> received.offer(new String(message.getBody())),
                new ChannelTopic(PlayerRedisProtocol.Channels.interactions(PLAYER_ID)));
        container.afterPropertiesSet();
        container.start();

        try {
            MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID).orElseThrow();
            player.join(new MusicPlayerJoinResultHandler() {
                public void onReady() {}
                public void onFailed(String reason) {}
            });

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();

            MusicPlayerInteraction interaction = objectMapper.readValue(json, MusicPlayerInteraction.class);
            assertThat(interaction).isInstanceOf(MusicPlayerInteraction.Connect.class);
            MusicPlayerInteraction.Connect connect = (MusicPlayerInteraction.Connect) interaction;
            assertThat(connect.playerId()).isEqualTo(PLAYER_ID);
            assertThat(connect.guildId()).isEqualTo(GUILD_ID);
            assertThat(connect.voiceChannelId()).isEqualTo(VOICE_CHANNEL_ID);
            assertThat(connect.requestId()).isNotBlank();
        } finally {
            container.stop();
        }
    }

    @Test
    void stopDeletesAssignmentKey() {
        MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID).orElseThrow();
        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID))).isNotNull();

        player.stop();

        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID))).isNull();
    }

    @Test
    void stopPublishesStopToCorrectRedisChannel() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(
                (message, pattern) -> received.offer(new String(message.getBody())),
                new ChannelTopic(PlayerRedisProtocol.Channels.interactions(PLAYER_ID)));
        container.afterPropertiesSet();
        container.start();

        try {
            MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID).orElseThrow();
            player.stop();

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();

            MusicPlayerInteraction interaction = objectMapper.readValue(json, MusicPlayerInteraction.class);
            assertThat(interaction).isInstanceOf(MusicPlayerInteraction.Stop.class);
            MusicPlayerInteraction.Stop stop = (MusicPlayerInteraction.Stop) interaction;
            assertThat(stop.playerId()).isEqualTo(PLAYER_ID);
            assertThat(stop.guildId()).isEqualTo(GUILD_ID);
            assertThat(stop.requestId()).isNotBlank();
        } finally {
            container.stop();
        }
    }
}
