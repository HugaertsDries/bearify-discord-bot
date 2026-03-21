package com.bearify.controller.music.discord;

import com.bearify.controller.AbstractControllerIntegrationTest;
import com.bearify.discord.spring.CommandRegistry;
import com.bearify.discord.testing.MockCommandInteraction;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerInteractionIntegrationTest extends AbstractControllerIntegrationTest {

    private static final String VOICE_CHANNEL_ID = "vc-123";
    private static final String GUILD_ID = "guild-456";
    private static final String PLAYER_ID = "player-1";

    @Autowired CommandRegistry commandRegistry;
    @Autowired RedisConnectionFactory redisConnectionFactory;
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void seedPlayer() {
        redis.delete(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS);
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        redis.opsForSet().add(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS, PLAYER_ID);
    }

    // --- JOIN: HAPPY PATH ---

    @Test
    void dispatchesSummonAndClaimsPlayer() {
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("join")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .build();

        commandRegistry.handle(interaction);

        assertThat(redis.opsForSet().isMember(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS, PLAYER_ID)).isTrue();
        assertThat(interaction.getDeferredMessage()).isPresent();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Bearify is padding over to your voice channel");
    }

    @Test
    void publishesConnectInteractionToPlayerChannel() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                    .subcommand("join")
                    .voiceChannelId(VOICE_CHANNEL_ID)
                    .guildId(GUILD_ID)
                    .build();

            commandRegistry.handle(interaction);

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();

            MusicPlayerInteraction playerInteraction = objectMapper.readValue(json, MusicPlayerInteraction.class);
            assertThat(playerInteraction).isInstanceOf(MusicPlayerInteraction.Connect.class);
            MusicPlayerInteraction.Connect connect = (MusicPlayerInteraction.Connect) playerInteraction;
            assertThat(connect.playerId()).isEqualTo(PLAYER_ID);
            assertThat(connect.requestId()).isNotBlank();
            assertThat(connect.voiceChannelId()).isEqualTo(VOICE_CHANNEL_ID);
            assertThat(connect.guildId()).isEqualTo(GUILD_ID);
        } finally {
            container.stop();
        }
    }

    @Test
    void showsConnectFailedMessageWhenJoinFails() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                    .subcommand("join")
                    .voiceChannelId(VOICE_CHANNEL_ID)
                    .guildId(GUILD_ID)
                    .build();

            commandRegistry.handle(interaction);

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();
            MusicPlayerInteraction.Connect connect = (MusicPlayerInteraction.Connect) objectMapper.readValue(json, MusicPlayerInteraction.class);

            String failedEvent = objectMapper.writeValueAsString(
                    new MusicPlayerEvent.ConnectFailed(PLAYER_ID, connect.requestId(), "connection refused"));
            redis.convertAndSend(PlayerRedisProtocol.Channels.EVENTS, failedEvent);

            Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                            .contains("The bear couldn't reach your channel"));
        } finally {
            container.stop();
        }
    }

    // --- JOIN: EDGE CASES ---

    @Test
    void showsDeferredMessageWhenNoPlayerAvailable() {
        redis.delete(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS);

        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("join")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .build();

        commandRegistry.handle(interaction);

        assertThat(interaction.getDeferredMessage()).isPresent();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("No music bears are free right now");
    }

    @Test
    void reusesAssignedPlayerForSameVoiceChannel() throws Exception {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                    .subcommand("join")
                    .voiceChannelId(VOICE_CHANNEL_ID)
                    .guildId(GUILD_ID)
                    .build();

            commandRegistry.handle(interaction);

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();
            assertThat(redis.opsForSet().isMember(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS, PLAYER_ID)).isTrue();
        } finally {
            container.stop();
        }
    }

    // --- LEAVE: HAPPY PATH ---

    @Test
    void publishesStopInteractionOnLeave() throws Exception {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                    .subcommand("leave")
                    .voiceChannelId(VOICE_CHANNEL_ID)
                    .guildId(GUILD_ID)
                    .build();

            commandRegistry.handle(interaction);

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();

            MusicPlayerInteraction playerInteraction = objectMapper.readValue(json, MusicPlayerInteraction.class);
            assertThat(playerInteraction).isInstanceOf(MusicPlayerInteraction.Stop.class);
            MusicPlayerInteraction.Stop stop = (MusicPlayerInteraction.Stop) playerInteraction;
            assertThat(stop.guildId()).isEqualTo(GUILD_ID);
        } finally {
            container.stop();
        }
    }

    // --- LEAVE: EDGE CASES ---

    @Test
    void showsNoPlayerMessageWhenNotAssigned() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));

        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("leave")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .build();

        commandRegistry.handle(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("No bear is playing");
    }

    // --- HELPERS ---

    private RedisMessageListenerContainer startListener(String topic, BlockingQueue<String> queue) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(
                (message, pattern) -> queue.offer(new String(message.getBody())),
                new ChannelTopic(topic));
        container.afterPropertiesSet();
        container.start();
        return container;
    }

}
