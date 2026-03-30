package com.bearify.controller.music.discord;

import com.bearify.controller.AbstractControllerIntegrationTest;
import com.bearify.discord.testing.MockDiscordClient;
import com.bearify.discord.testing.MockButtonInteraction;
import com.bearify.discord.spring.CommandRegistry;
import com.bearify.discord.testing.MockCommandInteraction;
import com.bearify.discord.api.message.ComponentMessage;
import com.bearify.discord.api.message.Container;
import com.bearify.discord.api.message.ContainerChild;
import com.bearify.discord.api.message.Section;
import com.bearify.discord.api.message.TextBlock;
import com.bearify.music.player.bridge.events.JoinRequest;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.model.Request;
import com.bearify.music.player.bridge.model.TrackMetadata;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "music-player.pool.connect-request-ttl=200ms")
class MusicPlayerInteractionIntegrationTest extends AbstractControllerIntegrationTest {

    private static final String VOICE_CHANNEL_ID = "vc-123";
    private static final String GUILD_ID = "guild-456";
    private static final String PLAYER_ID = "player-1";

    @Autowired CommandRegistry commandRegistry;
    @Autowired RedisConnectionFactory redisConnectionFactory;
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper objectMapper;
    @Autowired MockDiscordClient.Factory discordClientFactory;

    @BeforeEach
    void seedPlayer() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
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

        assertThat(interaction.getDeferredMessage()).isPresent();
        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains("Sending a bear your way");
    }

    @Test
    void publishesConnectInteractionToPlayerChannelWhenAlreadyAssigned() throws Exception {
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
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.REQUESTS, received);

        try {
            MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                    .subcommand("join")
                    .voiceChannelId(VOICE_CHANNEL_ID)
                    .guildId(GUILD_ID)
                    .build();

            commandRegistry.handle(interaction);

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();
            JoinRequest request = objectMapper.readValue(json, JoinRequest.class);

            String failedEvent = objectMapper.writeValueAsString(
                    new MusicPlayerEvent.ConnectFailed(PLAYER_ID, request.requestId(), "connection refused"));
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
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("join")
                .voiceChannelId(VOICE_CHANNEL_ID)
                .guildId(GUILD_ID)
                .build();

        commandRegistry.handle(interaction);

        assertThat(interaction.getDeferredMessage()).isPresent();
        assertThat(interaction.isDeferredEphemeral()).isFalse();
        Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                        .contains("No music bears are free right now. Try again in a moment."));
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
        assertThat(interaction.getReplies().getFirst().getContent()).contains("not even playing songs");
    }

    @Test
    void playFromTwoTextChannelsSubscribesBothChannelsToTheSamePlayer() throws Exception {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);

        MockCommandInteraction first = MockCommandInteraction.forCommand("player")
                .subcommand("play")
                .guildId(GUILD_ID)
                .voiceChannelId(VOICE_CHANNEL_ID)
                .textChannelId("text-1")
                .option("search", "song a")
                .build();
        MockCommandInteraction second = MockCommandInteraction.forCommand("player")
                .subcommand("play")
                .guildId(GUILD_ID)
                .voiceChannelId(VOICE_CHANNEL_ID)
                .textChannelId("text-2")
                .option("search", "song b")
                .build();

        commandRegistry.handle(first);
        commandRegistry.handle(second);

        redis.convertAndSend(PlayerRedisProtocol.Channels.EVENTS, objectMapper.writeValueAsString(
                new MusicPlayerEvent.TrackStart(
                        PLAYER_ID,
                        new Request("event-1", "@user"),
                        GUILD_ID,
                        new TrackMetadata("Song", "Artist", "https://example.com", 60_000), List.of())));

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            MockDiscordClient discord = discordClientFactory.getLastCreated().orElseThrow();
            assertThat(discord.sentComponents("text-1")).hasSize(1);
            assertThat(discord.sentComponents("text-2")).hasSize(1);
        });
    }

    @Test
    void announcerUpdatesEmbedDescriptionWhenPausedEventArrives() throws Exception {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);

        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("play")
                .guildId(GUILD_ID)
                .voiceChannelId(VOICE_CHANNEL_ID)
                .textChannelId("text-1")
                .option("search", "song a")
                .build();

        commandRegistry.handle(interaction);

        redis.convertAndSend(PlayerRedisProtocol.Channels.EVENTS, objectMapper.writeValueAsString(
                new MusicPlayerEvent.TrackStart(
                        PLAYER_ID,
                        new Request("event-1", "@user"),
                        GUILD_ID,
                        new TrackMetadata("Song", "Artist", "https://example.com", 60_000), List.of())));
        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            MockDiscordClient discord = discordClientFactory.getLastCreated().orElseThrow();
            assertThat(discord.sentComponents("text-1")).hasSize(1);
        });
        redis.convertAndSend(PlayerRedisProtocol.Channels.EVENTS, objectMapper.writeValueAsString(
                new MusicPlayerEvent.Paused(PLAYER_ID, new Request("event-2", "@user"), GUILD_ID)));

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            MockDiscordClient discord = discordClientFactory.getLastCreated().orElseThrow();
            assertThat(discord.sentComponents("text-1")).hasSize(1);
            assertThat(allTexts(discord.sentComponents("text-1").getFirst())).anyMatch(text -> text.contains("\u26AA ON THE AIR"));
        });
    }

    @Test
    void announcerShowsTemporarySkippedActionWhenSkippedEventArrives() throws Exception {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);

        MockCommandInteraction interaction = MockCommandInteraction.forCommand("player")
                .subcommand("play")
                .guildId(GUILD_ID)
                .voiceChannelId(VOICE_CHANNEL_ID)
                .textChannelId("text-1")
                .option("search", "song a")
                .build();

        commandRegistry.handle(interaction);

        redis.convertAndSend(PlayerRedisProtocol.Channels.EVENTS, objectMapper.writeValueAsString(
                new MusicPlayerEvent.TrackStart(
                        PLAYER_ID,
                        new Request("event-1", "@user"),
                        GUILD_ID,
                        new TrackMetadata("Song", "Artist", "https://example.com", 60_000), List.of())));
        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            MockDiscordClient discord = discordClientFactory.getLastCreated().orElseThrow();
            assertThat(discord.sentComponents("text-1")).hasSize(1);
        });
        redis.convertAndSend(PlayerRedisProtocol.Channels.EVENTS, objectMapper.writeValueAsString(
                new MusicPlayerEvent.Skipped(PLAYER_ID, new Request("event-2", "@user"), GUILD_ID)));

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            MockDiscordClient discord = discordClientFactory.getLastCreated().orElseThrow();
            assertThat(discord.sentComponents("text-1")).hasSize(1);
            assertThat(allTexts(discord.sentComponents("text-1").getFirst())).anyMatch(text -> text.contains("Last track skipped by @user"));
        });
    }

    @Test
    void buttonInteractionPublishesNextCommandForAssignedPlayer() throws Exception {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = startListener(PlayerRedisProtocol.Channels.interactions(PLAYER_ID), received);

        try {
            MockButtonInteraction interaction = MockButtonInteraction.forButton("player:next")
                    .guildId(GUILD_ID)
                    .voiceChannelId(VOICE_CHANNEL_ID)
                    .userMention("@button-user")
                    .build();

            MockDiscordClient client = discordClientFactory.getLastCreated().orElseThrow();
            client.dispatchButton(interaction);

            String json = received.poll(2, TimeUnit.SECONDS);
            assertThat(json).isNotNull();

            MusicPlayerInteraction playerInteraction = objectMapper.readValue(json, MusicPlayerInteraction.class);
            assertThat(playerInteraction).isInstanceOf(MusicPlayerInteraction.Next.class);
            MusicPlayerInteraction.Next next = (MusicPlayerInteraction.Next) playerInteraction;
            assertThat(next.guildId()).isEqualTo(GUILD_ID);
            assertThat(next.request().requesterTag()).isEqualTo("@button-user");
            assertThat(interaction.isAcknowledged()).isTrue();
            assertThat(interaction.getDeferredMessage()).isEmpty();
            assertThat(interaction.getReplies()).isEmpty();
        } finally {
            container.stop();
        }
    }

    private static List<String> allTexts(ComponentMessage message) {
        List<String> texts = new java.util.ArrayList<>();
        for (Container container : message.containers()) {
            for (ContainerChild child : container.children()) {
                switch (child) {
                    case TextBlock textBlock -> texts.add(textBlock.text());
                    case Section section -> texts.addAll(section.texts());
                    default -> {
                    }
                }
            }
        }
        return texts;
    }

}
