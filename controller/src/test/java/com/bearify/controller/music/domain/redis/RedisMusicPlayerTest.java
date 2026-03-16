package com.bearify.controller.music.domain.redis;

import com.bearify.controller.AbstractControllerIntegrationTest;
import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.shared.events.MusicPlayerInteraction;
import com.bearify.shared.player.PlayerRedisProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RedisMusicPlayerTest extends AbstractControllerIntegrationTest {

    private static final String PLAYER_ID = "player-1";
    private static final String GUILD_ID = "guild-1";
    private static final String VOICE_CHANNEL_ID = "voice-1";

    @Autowired MusicPlayerPool pool;
    @Autowired RedisConnectionFactory redisConnectionFactory;
    @Autowired com.bearify.shared.player.PlayerMessageCodec codec;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void seedPlayer() {
        redis.delete(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS);
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        redis.opsForSet().add(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS, PLAYER_ID);
    }

    // --- HAPPY PATH ---

    @Test
    void joinReturnsNonNullFuture() {
        MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID).orElseThrow();

        CompletableFuture<?> future = player.join();

        assertThat(future).isNotNull();
    }

    @Test
    void joinPublishesConnectToCorrectRedisChannel() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(
                (message, pattern) -> received.offer(new String(message.getBody())),
                new ChannelTopic(PlayerRedisProtocol.Channels.commands(PLAYER_ID)));
        container.afterPropertiesSet();
        container.start();

        try {
            MusicPlayer player = pool.acquire(GUILD_ID, VOICE_CHANNEL_ID).orElseThrow();
            player.join();

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();

            MusicPlayerInteraction command = codec.parseInteraction(json.getBytes());
            assertThat(command).isInstanceOf(MusicPlayerInteraction.Connect.class);
            MusicPlayerInteraction.Connect connect = (MusicPlayerInteraction.Connect) command;
            assertThat(connect.playerId()).isEqualTo(PLAYER_ID);
            assertThat(connect.guildId()).isEqualTo(GUILD_ID);
            assertThat(connect.voiceChannelId()).isEqualTo(VOICE_CHANNEL_ID);
            assertThat(connect.requestId()).isNotBlank();
        } finally {
            container.stop();
        }
    }
}
