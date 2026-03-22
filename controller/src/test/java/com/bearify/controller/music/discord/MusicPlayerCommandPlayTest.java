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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerCommandPlayTest extends AbstractControllerIntegrationTest {

    private static final String VOICE_CHANNEL_ID = "vc-123";
    private static final String GUILD_ID = "guild-456";
    private static final String TEXT_CHANNEL_ID = "txt-789";
    private static final String PLAYER_ID = "player-1";
    private static final String QUERY = "never gonna give you up";
    private static final String RESOLVED_QUERY = "ytsearch:" + QUERY;

    @Autowired CommandRegistry commandRegistry;
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void seedPlayer() {
        redis.delete(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS);
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        redis.opsForSet().add(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS, PLAYER_ID);
    }

    // --- HAPPY PATH ---

    @Test
    void playsTrackWhenPlayerAlreadyInChannel() throws Exception {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                    .subcommand("play")
                    .voiceChannelId(VOICE_CHANNEL_ID)
                    .guildId(GUILD_ID)
                    .textChannelId(TEXT_CHANNEL_ID)
                    .option("search", QUERY)
                    .build();

            commandRegistry.handle(interaction);

            assertThat(interaction.getDeferredMessage()).isPresent();
            assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                    .contains("added your track to the mix");

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();
            MusicPlayerInteraction playerInteraction = objectMapper.readValue(json, MusicPlayerInteraction.class);
            assertThat(playerInteraction).isInstanceOf(MusicPlayerInteraction.Play.class);
            MusicPlayerInteraction.Play play = (MusicPlayerInteraction.Play) playerInteraction;
            assertThat(play.query()).isEqualTo(RESOLVED_QUERY);
            assertThat(play.textChannelId()).isEqualTo(TEXT_CHANNEL_ID);
        } finally {
            container.stop();
        }
    }

    @Test
    void autoJoinsAndThenPlaysWhenNoPlayerAssigned() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                    .subcommand("play")
                    .voiceChannelId(VOICE_CHANNEL_ID)
                    .guildId(GUILD_ID)
                    .textChannelId(TEXT_CHANNEL_ID)
                    .option("search", QUERY)
                    .build();

            commandRegistry.handle(interaction);

            // First message is deferred with JOINING
            assertThat(interaction.getDeferredMessage()).isPresent();
            assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                    .contains("Sending a bear your way");

            // Connect interaction is sent to player
            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();
            MusicPlayerInteraction.Connect connect = (MusicPlayerInteraction.Connect) objectMapper.readValue(json, MusicPlayerInteraction.class);

            // Simulate player becoming ready
            String readyEvent = objectMapper.writeValueAsString(
                    new MusicPlayerEvent.Ready(PLAYER_ID, connect.requestId()));
            redis.convertAndSend(PlayerRedisProtocol.Channels.EVENTS, readyEvent);

            // After ready: play interaction sent and deferred message updated
            String playJson = received.poll(2, TimeUnit.SECONDS);
            assertThat(playJson).isNotNull();
            MusicPlayerInteraction.Play play = (MusicPlayerInteraction.Play) objectMapper.readValue(playJson, MusicPlayerInteraction.class);
            assertThat(play.query()).isEqualTo(RESOLVED_QUERY);
            assertThat(play.textChannelId()).isEqualTo(TEXT_CHANNEL_ID);

            Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                            .contains("added your track to the mix"));
        } finally {
            container.stop();
        }
    }

    // --- EDGE CASES ---

    @Test
    void rejectsPlayWhenUserNotInVoiceChannel() {
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("play")
                .guildId(GUILD_ID)
                .textChannelId(TEXT_CHANNEL_ID)
                .option("search", QUERY)
                .build();

        commandRegistry.handle(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("voice channel");
    }

    @Test
    void rejectsPlayWhenNoPlayersAvailable() {
        redis.delete(PlayerRedisProtocol.Keys.AVAILABLE_PLAYERS);

        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("play")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .textChannelId(TEXT_CHANNEL_ID)
                .option("search", QUERY)
                .build();

        commandRegistry.handle(interaction);

        assertThat(interaction.getDeferredMessage()).isPresent();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("No music bears are free right now");
    }

    @Test
    void rejectsPlayWhenJoinFails() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                    .subcommand("play")
                    .voiceChannelId(VOICE_CHANNEL_ID)
                    .guildId(GUILD_ID)
                    .textChannelId(TEXT_CHANNEL_ID)
                    .option("search", QUERY)
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

}
