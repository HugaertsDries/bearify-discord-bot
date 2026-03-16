package com.bearify.music.player.agent.discord;

import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.DiscordClientFactory;
import com.bearify.discord.api.gateway.GuildClient;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.model.CommandDefinition;
import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.discord.api.voice.VoiceSessionListener;
import com.bearify.music.player.agent.domain.ConnectionRequest;
import com.bearify.music.player.agent.domain.VoiceConnectionManager;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.protocol.PlayerMessageCodec;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "discord.token=test-token",
        "player.id=test-player"
})
@Import(DiscordVoiceConnectionManagerTest.TestConfig.class)
class DiscordVoiceConnectionManagerTest {

    private static final String PLAYER_ID = "test-player";
    private static final String REQUEST_ID = "req-1";
    private static final String VOICE_CHANNEL_ID = "voice-1";
    private static final String GUILD_ID = "guild-1";
    private static final String GUILD_ID_2 = "guild-2";

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired VoiceConnectionManager voiceConnectionManager;
    @Autowired FakeDiscordClient discordClient;
    @Autowired RedisConnectionFactory connectionFactory;
    @Autowired PlayerMessageCodec codec;

    private RedisMessageListenerContainer container;

    @AfterEach
    void stopContainer() {
        if (container != null) {
            container.stop();
            container = null;
        }
        discordClient.reset();
    }

    // --- HAPPY PATH ---

    @Test
    void joinsRequestedVoiceChannelWhenConnecting() {
        voiceConnectionManager.connect(new ConnectionRequest(REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));

        FakeVoiceSession session = discordClient.session(GUILD_ID);
        assertThat(session.getJoinedChannelId()).isEqualTo(VOICE_CHANNEL_ID);
        assertThat(session.hasJoinListener()).isTrue();
    }

    @Test
    void publishesReadyEventWhenVoiceSessionReportsJoined() throws Exception {
        AtomicReference<MusicPlayerEvent> received = new AtomicReference<>();
        startListener(body -> received.set(codec.parseEvent(body)));

        voiceConnectionManager.connect(new ConnectionRequest(REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));
        discordClient.session(GUILD_ID).simulateJoined(VOICE_CHANNEL_ID);

        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(received.get()).isEqualTo(new MusicPlayerEvent.Ready(PLAYER_ID, REQUEST_ID)));
    }

    @Test
    void publishesReadyEventImmediatelyWhenAlreadyConnected() throws Exception {
        AtomicReference<MusicPlayerEvent> received = new AtomicReference<>();
        startListener(body -> received.set(codec.parseEvent(body)));
        discordClient.session(GUILD_ID).join(VOICE_CHANNEL_ID, channelId -> {});

        voiceConnectionManager.connect(new ConnectionRequest(REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));

        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(received.get()).isEqualTo(new MusicPlayerEvent.Ready(PLAYER_ID, REQUEST_ID)));
    }

    @Test
    void connectsToMultipleGuildsSimultaneously() {
        voiceConnectionManager.connect(new ConnectionRequest(REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));
        voiceConnectionManager.connect(new ConnectionRequest("req-2", "voice-2", GUILD_ID_2));

        assertThat(discordClient.session(GUILD_ID).getJoinedChannelId()).isEqualTo(VOICE_CHANNEL_ID);
        assertThat(discordClient.session(GUILD_ID_2).getJoinedChannelId()).isEqualTo("voice-2");
    }

    // --- LIFECYCLE ---

    @Test
    void leavesConnectedGuildWhenDisconnecting() {
        voiceConnectionManager.connect(new ConnectionRequest(REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));

        voiceConnectionManager.disconnect(GUILD_ID);

        assertThat(discordClient.session(GUILD_ID).getLeaveCount()).isEqualTo(1);
    }

    @Test
    void doesNothingWhenDisconnectingWithoutActiveGuild() {
        voiceConnectionManager.disconnect(GUILD_ID);

        assertThat(discordClient.session(GUILD_ID).getLeaveCount()).isZero();
    }

    @Test
    void disconnectsOnlyOnceForSameGuild() {
        voiceConnectionManager.connect(new ConnectionRequest(REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));

        voiceConnectionManager.disconnect(GUILD_ID);
        voiceConnectionManager.disconnect(GUILD_ID);

        assertThat(discordClient.session(GUILD_ID).getLeaveCount()).isEqualTo(1);
    }

    @Test
    void disconnectsOnlyTargetedGuild() {
        voiceConnectionManager.connect(new ConnectionRequest(REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));
        voiceConnectionManager.connect(new ConnectionRequest("req-2", "voice-2", GUILD_ID_2));

        voiceConnectionManager.disconnect(GUILD_ID);

        assertThat(discordClient.session(GUILD_ID).getLeaveCount()).isEqualTo(1);
        assertThat(discordClient.session(GUILD_ID_2).getLeaveCount()).isZero();
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

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FakeDiscordClient fakeDiscordClient() {
            return new FakeDiscordClient();
        }

        @Bean
        DiscordClientFactory discordClientFactory(FakeDiscordClient client) {
            return new FakeDiscordClientFactory(client);
        }
    }

    static final class FakeDiscordClientFactory implements DiscordClientFactory {

        private final FakeDiscordClient client;

        private FakeDiscordClientFactory(FakeDiscordClient client) {
            this.client = client;
        }

        @Override
        public DiscordClient create(List<CommandDefinition> commands, Consumer<CommandInteraction> handler) {
            return client;
        }
    }

    static final class FakeDiscordClient implements DiscordClient {

        private final Map<String, FakeVoiceSession> sessions = new HashMap<>();

        @Override
        public void start(String token) {
        }

        @Override
        public void start(String token, String guildId) {
        }

        @Override
        public GuildClient guild(String guildId) {
            return () -> sessions.computeIfAbsent(guildId, FakeVoiceSession::new);
        }

        @Override
        public void shutdown() {
        }

        FakeVoiceSession session(String guildId) {
            return sessions.computeIfAbsent(guildId, FakeVoiceSession::new);
        }

        void reset() {
            sessions.clear();
        }
    }

    static final class FakeVoiceSession implements VoiceSession {

        private final String guildId;
        private String joinedChannelId;
        private int leaveCount;
        private VoiceSessionListener joinListener;

        private FakeVoiceSession(String guildId) {
            this.guildId = guildId;
        }

        @Override
        public void join(String channelId, VoiceSessionListener onJoined) {
            joinedChannelId = channelId;
            joinListener = onJoined;
        }

        @Override
        public void leave() {
            leaveCount++;
        }

        @Override
        public Optional<String> getConnectedChannelId() {
            return joinedChannelId != null && leaveCount == 0
                    ? Optional.of(joinedChannelId)
                    : Optional.empty();
        }

        @Override
        public boolean isAlone() {
            return true;
        }

        @Override
        public String getGuildId() {
            return guildId;
        }

        void simulateJoined(String channelId) {
            if (joinListener != null) {
                joinListener.onJoined(channelId);
            }
        }

        boolean hasJoinListener() {
            return joinListener != null;
        }

        String getJoinedChannelId() {
            return joinedChannelId;
        }

        int getLeaveCount() {
            return leaveCount;
        }
    }

    @FunctionalInterface
    private interface MessageHandler {
        void handle(byte[] body);
    }
}
