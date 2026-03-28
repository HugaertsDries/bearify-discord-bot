package com.bearify.music.player.agent.domain;

import com.bearify.discord.api.gateway.Activity;
import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.DiscordClientFactory;
import com.bearify.discord.api.gateway.EmbedMessage;
import com.bearify.discord.api.gateway.Guild;
import com.bearify.discord.api.gateway.SentMessage;
import com.bearify.discord.api.gateway.TextChannel;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.model.CommandDefinition;
import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.discord.api.voice.VoiceSessionListener;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import com.bearify.music.player.bridge.events.JoinRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "discord.token=test-token",
        "player.id=test-player"
})
@Import(VoiceConnectionManagerTest.TestConfig.class)
class VoiceConnectionManagerTest {

    private static final String PLAYER_ID = "test-player";
    private static final String REQUEST_ID = "req-1";
    private static final String VOICE_CHANNEL_ID = "voice-1";
    private static final String GUILD_ID = "guild-1";
    private static final String GUILD_ID_2 = "guild-2";
    private static final String VOICE_CHANNEL_ID_2 = "voice-2";

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
    @Autowired ObjectMapper objectMapper;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;

    private RedisMessageListenerContainer container;

    @BeforeEach
    void resetState() {
        voiceConnectionManager.disconnect(GUILD_ID);
        voiceConnectionManager.disconnect(GUILD_ID_2);
        discordClient.reset();
    }

    @AfterEach
    void stopContainer() {
        if (container != null) {
            container.stop();
            container = null;
        }
        discordClient.reset();
    }

    @AfterEach
    void cleanupAssignmentKeys() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID_2));
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID_2, "voice-2"));
        redis.delete(PlayerRedisProtocol.Keys.connectRequest(REQUEST_ID));
        discordClient.guild(GUILD_ID).setLonely(true);
    }

    // --- HAPPY PATH ---

    @Test
    void joinsRequestedVoiceChannelWhenConnecting() {
        voiceConnectionManager.connect(new ConnectionRequest(REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));

        FakeGuild guild = discordClient.guild(GUILD_ID);
        assertThat(guild.getJoinedChannelId()).isEqualTo(VOICE_CHANNEL_ID);
        assertThat(guild.hasJoinListener()).isTrue();
    }

    @Test
    void publishesReadyEventWhenVoiceSessionReportsJoined() throws Exception {
        AtomicReference<MusicPlayerEvent> received = new AtomicReference<>();
        startListener(body -> received.set(parseEvent(body)));

        voiceConnectionManager.connect(new ConnectionRequest(REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));
        discordClient.guild(GUILD_ID).simulateJoined(VOICE_CHANNEL_ID);

        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(received.get()).isEqualTo(new MusicPlayerEvent.Ready(PLAYER_ID, REQUEST_ID)));
    }

    @Test
    void publishesReadyEventImmediatelyWhenAlreadyConnected() throws Exception {
        AtomicReference<MusicPlayerEvent> received = new AtomicReference<>();
        startListener(body -> received.set(parseEvent(body)));
        discordClient.guild(GUILD_ID).simulateJoined(VOICE_CHANNEL_ID);

        voiceConnectionManager.connect(new ConnectionRequest(REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));

        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(received.get()).isEqualTo(new MusicPlayerEvent.Ready(PLAYER_ID, REQUEST_ID)));
    }

    @Test
    void connectsToMultipleGuildsSimultaneously() {
        voiceConnectionManager.connect(new ConnectionRequest(REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));
        voiceConnectionManager.connect(new ConnectionRequest("req-2", "voice-2", GUILD_ID_2));

        assertThat(discordClient.guild(GUILD_ID).getJoinedChannelId()).isEqualTo(VOICE_CHANNEL_ID);
        assertThat(discordClient.guild(GUILD_ID_2).getJoinedChannelId()).isEqualTo("voice-2");
    }

    // --- LIFECYCLE ---

    @Test
    void leavesConnectedGuildWhenDisconnecting() {
        voiceConnectionManager.connect(new ConnectionRequest(REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));
        discordClient.guild(GUILD_ID).simulateJoined(VOICE_CHANNEL_ID);

        voiceConnectionManager.disconnect(GUILD_ID);

        assertThat(discordClient.guild(GUILD_ID).getLeaveCount()).isEqualTo(1);
    }

    @Test
    void doesNothingWhenDisconnectingWithoutActiveGuild() {
        voiceConnectionManager.disconnect(GUILD_ID);

        assertThat(discordClient.guild(GUILD_ID).getLeaveCount()).isZero();
    }

    @Test
    void disconnectsOnlyOnceForSameGuild() {
        voiceConnectionManager.connect(new ConnectionRequest(REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));
        discordClient.guild(GUILD_ID).simulateJoined(VOICE_CHANNEL_ID);

        voiceConnectionManager.disconnect(GUILD_ID);
        voiceConnectionManager.disconnect(GUILD_ID);

        assertThat(discordClient.guild(GUILD_ID).getLeaveCount()).isEqualTo(1);
    }

    @Test
    void disconnectsOnlyTargetedGuild() {
        voiceConnectionManager.connect(new ConnectionRequest(REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));
        voiceConnectionManager.connect(new ConnectionRequest("req-2", "voice-2", GUILD_ID_2));
        discordClient.guild(GUILD_ID).simulateJoined(VOICE_CHANNEL_ID);
        discordClient.guild(GUILD_ID_2).simulateJoined("voice-2");

        voiceConnectionManager.disconnect(GUILD_ID);

        assertThat(discordClient.guild(GUILD_ID).getLeaveCount()).isEqualTo(1);
        assertThat(discordClient.guild(GUILD_ID_2).getLeaveCount()).isZero();
    }

    // --- BUG FIXES ---

    @Test
    void tracksGuildAsConnectedOnlyAfterJoinCallbackFires() {
        voiceConnectionManager.connect(new ConnectionRequest(REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));

        voiceConnectionManager.disconnect(GUILD_ID);
        assertThat(discordClient.guild(GUILD_ID).getLeaveCount()).isZero();

        discordClient.guild(GUILD_ID).simulateJoined(VOICE_CHANNEL_ID);

        voiceConnectionManager.disconnect(GUILD_ID);
        assertThat(discordClient.guild(GUILD_ID).getLeaveCount()).isEqualTo(1);
    }

    @Test
    void tracksGuildAfterMovingToNewChannel() {
        discordClient.guild(GUILD_ID).simulateJoined(VOICE_CHANNEL_ID);

        voiceConnectionManager.connect(new ConnectionRequest(REQUEST_ID, "voice-2", GUILD_ID));
        discordClient.guild(GUILD_ID).simulateJoined("voice-2");

        voiceConnectionManager.disconnect(GUILD_ID);
        assertThat(discordClient.guild(GUILD_ID).getLeaveCount()).isEqualTo(1);
    }

    // --- ASSIGNMENT KEY CLEANUP ---

    @Test
    void deletesAssignmentKeyOnDisconnect() {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), "test-player");
        voiceConnectionManager.connect(new ConnectionRequest(REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));
        discordClient.guild(GUILD_ID).simulateJoined(VOICE_CHANNEL_ID);

        voiceConnectionManager.disconnect(GUILD_ID);

        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID))).isNull();
    }

    @Test
    void doesNotDeleteAssignmentKeyWhenNotConnected() {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), "test-player");

        voiceConnectionManager.disconnect(GUILD_ID);

        // No voice session active → key should not be touched
        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID))).isEqualTo("test-player");
    }

    @Test
    void disconnectsAllActiveGuilds() {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), "test-player");
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID_2, "voice-2"), "test-player");
        voiceConnectionManager.connect(new ConnectionRequest(REQUEST_ID, VOICE_CHANNEL_ID, GUILD_ID));
        voiceConnectionManager.connect(new ConnectionRequest("req-2", "voice-2", GUILD_ID_2));
        discordClient.guild(GUILD_ID).simulateJoined(VOICE_CHANNEL_ID);
        discordClient.guild(GUILD_ID_2).simulateJoined("voice-2");

        voiceConnectionManager.close();

        assertThat(discordClient.guild(GUILD_ID).getLeaveCount()).isEqualTo(1);
        assertThat(discordClient.guild(GUILD_ID_2).getLeaveCount()).isEqualTo(1);
        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID))).isNull();
        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID_2, "voice-2"))).isNull();
    }

    private void seedLivenessKey(String requestId) {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.connectRequest(requestId), "1");
    }

    // --- CLAIM ---

    @Test
    void skipsClaimWhenLivenessKeyIsExpired() {
        // No liveness key seeded — request is stale
        voiceConnectionManager.claim(new JoinRequest(REQUEST_ID, GUILD_ID, VOICE_CHANNEL_ID));

        assertThat(discordClient.guild(GUILD_ID).getJoinedChannelId()).isNull();
        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID))).isNull();
    }

    @Test
    void claimsAssignmentKeyAndJoinsWhenNotServingGuild() {
        seedLivenessKey(REQUEST_ID);

        voiceConnectionManager.claim(new JoinRequest(REQUEST_ID, GUILD_ID, VOICE_CHANNEL_ID));

        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID))).isEqualTo(PLAYER_ID);
        assertThat(discordClient.guild(GUILD_ID).getJoinedChannelId()).isEqualTo(VOICE_CHANNEL_ID);
    }

    @Test
    void skipsClaimWhenAssignmentKeyAlreadyTaken() {
        seedLivenessKey(REQUEST_ID);
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), "other-player");

        voiceConnectionManager.claim(new JoinRequest(REQUEST_ID, GUILD_ID, VOICE_CHANNEL_ID));

        assertThat(discordClient.guild(GUILD_ID).getJoinedChannelId()).isNull();
        // key still belongs to other-player
        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID))).isEqualTo("other-player");
    }

    @Test
    void dispatchesReadyEventWhenAlreadyInRequestedChannel() throws Exception {
        seedLivenessKey(REQUEST_ID);
        discordClient.guild(GUILD_ID).simulateJoined(VOICE_CHANNEL_ID);
        AtomicReference<MusicPlayerEvent> received = new AtomicReference<>();
        startListener(body -> received.set(parseEvent(body)));

        voiceConnectionManager.claim(new JoinRequest(REQUEST_ID, GUILD_ID, VOICE_CHANNEL_ID));

        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(received.get())
                        .isEqualTo(new MusicPlayerEvent.Ready(PLAYER_ID, REQUEST_ID)));
    }

    @Test
    void refreshesAssignmentKeyWhenAlreadyInRequestedChannel() {
        seedLivenessKey(REQUEST_ID);
        discordClient.guild(GUILD_ID).simulateJoined(VOICE_CHANNEL_ID);

        voiceConnectionManager.claim(new JoinRequest(REQUEST_ID, GUILD_ID, VOICE_CHANNEL_ID));

        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID))).isEqualTo(PLAYER_ID);
    }

    @Test
    void migratesToNewChannelWhenAloneInDifferentChannel() {
        seedLivenessKey(REQUEST_ID);
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), PLAYER_ID);
        discordClient.guild(GUILD_ID).simulateJoined(VOICE_CHANNEL_ID); // isLonely = true by default in FakeGuild

        voiceConnectionManager.claim(new JoinRequest(REQUEST_ID, GUILD_ID, VOICE_CHANNEL_ID_2));
        discordClient.guild(GUILD_ID).simulateJoined(VOICE_CHANNEL_ID_2);

        // new key written, old key deleted
        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID_2))).isEqualTo(PLAYER_ID);
        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID))).isNull();
        assertThat(discordClient.guild(GUILD_ID).getJoinedChannelId()).isEqualTo(VOICE_CHANNEL_ID_2);
    }

    @Test
    void rejectsClaimWhenNotAloneInDifferentChannel() {
        seedLivenessKey(REQUEST_ID);
        discordClient.guild(GUILD_ID).simulateJoined(VOICE_CHANNEL_ID);
        discordClient.guild(GUILD_ID).setLonely(false); // not alone

        voiceConnectionManager.claim(new JoinRequest(REQUEST_ID, GUILD_ID, VOICE_CHANNEL_ID_2));

        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID_2))).isNull();
        assertThat(discordClient.guild(GUILD_ID).getJoinedChannelId()).isNull(); // did not attempt join
    }

    @Test
    void handlesOnlyOneClaimPerGuildUnderConcurrentRequests() throws InterruptedException {
        seedLivenessKey(REQUEST_ID);
        redis.opsForValue().set(PlayerRedisProtocol.Keys.connectRequest("req-2"), "1");

        var latch = new java.util.concurrent.CountDownLatch(2);
        var t1 = new Thread(() -> { voiceConnectionManager.claim(new JoinRequest(REQUEST_ID, GUILD_ID, VOICE_CHANNEL_ID)); latch.countDown(); });
        var t2 = new Thread(() -> { voiceConnectionManager.claim(new JoinRequest("req-2", GUILD_ID, VOICE_CHANNEL_ID_2)); latch.countDown(); });
        t1.start(); t2.start();
        latch.await(5, TimeUnit.SECONDS);

        // Exactly one join call should have been made — second claim must be skipped.
        // FakeDiscordClient.FakeGuildClient tracks join call count via joinCallCount (AtomicInteger),
        // incremented on every join() call regardless of which channel. This catches the case where
        // both threads attempted to join (last-writer-wins on a simple field would mask a real bug).
        assertThat(discordClient.guild(GUILD_ID).joinCallCount()).isEqualTo(1);

        redis.delete(PlayerRedisProtocol.Keys.connectRequest("req-2"));
    }

    private MusicPlayerEvent parseEvent(byte[] body) {
        try {
            return objectMapper.readValue(body, MusicPlayerEvent.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse MusicPlayerEvent", e);
        }
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

        @Override
        public DiscordClient create(List<CommandDefinition> commands, Consumer<CommandInteraction> handler, Activity activity) {
            return client;
        }
    }

    static final class FakeDiscordClient implements DiscordClient {

        private final Map<String, FakeGuild> guilds = new HashMap<>();

        @Override
        public void start(String token) {
        }

        @Override
        public void start(String token, String guildId) {
        }

        @Override
        public FakeGuild guild(String guildId) {
            return guilds.computeIfAbsent(guildId, id -> new FakeGuild());
        }

        @Override
        public TextChannel textChannel(String channelId) {
            return new TextChannel() {
                @Override public void send(String message) {}
                @Override public SentMessage send(EmbedMessage embed) {
                    return new SentMessage() {
                        @Override public void delete() {}
                        @Override public void update(EmbedMessage updated) {}
                    };
                }
            };
        }

        @Override
        public void shutdown() {
        }

        void reset() {
            guilds.clear();
        }
    }

    static final class FakeGuild implements Guild {

        private String joinedChannelId;
        private String connectedChannelId;
        private int leaveCount;
        private VoiceSessionListener joinListener;
        private boolean lonely = true;
        private final AtomicInteger joinCallCount = new AtomicInteger();

        void setLonely(boolean lonely) { this.lonely = lonely; }

        int joinCallCount() { return joinCallCount.get(); }

        @Override
        public Optional<VoiceSession> voice() {
            if (connectedChannelId != null) {
                return Optional.of(new VoiceSession() {
                    @Override public String getChannelId() { return connectedChannelId; }
                    @Override public boolean isLonely() { return lonely; }
                    @Override public void leave() { leaveCount++; connectedChannelId = null; }
                });
            }
            return Optional.empty();
        }

        @Override
        public void join(String channelId, AudioProvider provider, VoiceSessionListener onJoined) {
            joinCallCount.incrementAndGet();
            joinedChannelId = channelId;
            joinListener = onJoined;
        }

        void simulateJoined(String channelId) {
            connectedChannelId = channelId;
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
