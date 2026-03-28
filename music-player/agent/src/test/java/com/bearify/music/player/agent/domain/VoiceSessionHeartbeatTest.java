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
import com.bearify.music.player.agent.domain.VoiceSessionHeartbeatTest.TestConfig;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "discord.token=test-token",
        "player.id=test-player",
        "music-player.assignment.ttl=30s",
        "music-player.voice-session.heartbeat-interval=10s",
        "music-player.voice-session.lonely-timeout=5m",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.task.scheduling.enabled=false"
})
@Import(TestConfig.class)
class VoiceSessionHeartbeatTest {

    private static final String GUILD_ID = "guild-1";
    private static final String CHANNEL_ID = "voice-1";

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

    @Autowired AudioPlayerPool pool;
    @Autowired FakeDiscordClient discordClient;
    @Autowired TestVoiceConnectionManager recordingManager;
    @Autowired StringRedisTemplate redis;
    @Autowired VoiceSessionHeartbeat heartbeat;
    @Autowired MutableClock clock;

    @AfterEach
    void cleanup() {
        recordingManager.reset();
        discordClient.reset();
        clock.reset();
        ((Map<?, ?>) ReflectionTestUtils.getField(pool, "entries")).clear();
        ((Map<?, ?>) ReflectionTestUtils.getField(heartbeat, "lonelyGuilds")).clear();
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, CHANNEL_ID));
    }

    @Test
    void renewsAssignmentTtlWhenVoiceSessionIsActive() {
        pool.getOrCreate(GUILD_ID);
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, CHANNEL_ID), "test-player");

        heartbeat.maintain();

        Long expireSeconds = redis.getExpire(PlayerRedisProtocol.Keys.assignment(GUILD_ID, CHANNEL_ID));
        assertThat(expireSeconds).isGreaterThan(0);
    }

    @Test
    void leavesAssignmentUntouchedWhenNoVoiceSessionIsActive() throws InterruptedException {
        pool.getOrCreate(GUILD_ID);
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, CHANNEL_ID), "test-player", Duration.ofSeconds(2));
        Long ttlBefore = redis.getExpire(PlayerRedisProtocol.Keys.assignment(GUILD_ID, CHANNEL_ID));

        Thread.sleep(1100);
        heartbeat.maintain();

        Long ttlAfter = redis.getExpire(PlayerRedisProtocol.Keys.assignment(GUILD_ID, CHANNEL_ID));
        assertThat(ttlAfter).isLessThanOrEqualTo(ttlBefore);
        assertThat(recordingManager.getDisconnectedGuilds()).isEmpty();
    }

    @Test
    void doesNotDisconnectBeforeLonelyTimeoutExpires() {
        pool.getOrCreate(GUILD_ID);
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);

        heartbeat.maintain();
        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        heartbeat.maintain();

        assertThat(recordingManager.getDisconnectedGuilds()).isEmpty();
    }

    @Test
    void disconnectsWhenLonelyTimeoutExpires() {
        pool.getOrCreate(GUILD_ID);
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);

        heartbeat.maintain();
        clock.advance(Duration.ofMinutes(5));
        heartbeat.maintain();

        assertThat(recordingManager.getDisconnectedGuilds()).contains(GUILD_ID);
    }

    @Test
    void clearsLonelyTrackingWhenNonBotUserReturns() {
        pool.getOrCreate(GUILD_ID);
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);

        heartbeat.maintain();
        clock.advance(Duration.ofMinutes(4));
        discordClient.guild(GUILD_ID).setLonely(false);
        heartbeat.maintain();
        clock.advance(Duration.ofMinutes(4));
        discordClient.guild(GUILD_ID).setLonely(true);
        heartbeat.maintain();

        assertThat(recordingManager.getDisconnectedGuilds()).isEmpty();
    }

    @Test
    void clearsLonelyTrackingWhenSessionDisappears() {
        pool.getOrCreate(GUILD_ID);
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);

        heartbeat.maintain();
        clock.advance(Duration.ofMinutes(4));
        discordClient.guild(GUILD_ID).simulateLeft();
        heartbeat.maintain();
        clock.advance(Duration.ofMinutes(4));
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);
        heartbeat.maintain();

        assertThat(recordingManager.getDisconnectedGuilds()).isEmpty();
    }

    @Test
    void resetsLonelyTrackingWhenChannelChanges() {
        pool.getOrCreate(GUILD_ID);
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);

        heartbeat.maintain();
        clock.advance(Duration.ofMinutes(4));
        discordClient.guild(GUILD_ID).simulateJoined("voice-2");
        heartbeat.maintain();
        clock.advance(Duration.ofMinutes(4));
        heartbeat.maintain();

        assertThat(recordingManager.getDisconnectedGuilds()).isEmpty();
    }

    @Test
    void retriesDisconnectOnLaterTickWhenFirstAttemptFails() {
        pool.getOrCreate(GUILD_ID);
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);
        recordingManager.failNextDisconnect();

        heartbeat.maintain();
        clock.advance(Duration.ofMinutes(5));
        heartbeat.maintain();
        heartbeat.maintain();

        assertThat(recordingManager.disconnectCallsFor(GUILD_ID)).isEqualTo(2);
        assertThat(recordingManager.getDisconnectedGuilds()).contains(GUILD_ID);
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

        @Bean(name = {"testVoiceConnectionManager", "voiceConnectionManager"})
        @Primary
        TestVoiceConnectionManager testVoiceConnectionManager() {
            return new TestVoiceConnectionManager();
        }

        @Bean
        @Primary
        MutableClock clock() {
            return new MutableClock();
        }
    }

    static final class MutableClock extends Clock {
        private static final Instant INITIAL = Instant.parse("2026-03-26T12:00:00Z");

        private Instant current = INITIAL;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        void reset() {
            current = INITIAL;
        }
    }

    static final class TestVoiceConnectionManager extends VoiceConnectionManager {

        private final Set<String> disconnectedGuilds = new java.util.HashSet<>();
        private final Map<String, Integer> disconnectCalls = new HashMap<>();
        private boolean failNextDisconnect;

        TestVoiceConnectionManager() {
            super(null, null, null, null, null, "test");
        }

        @Override
        public void disconnect(String guildId) {
            disconnectCalls.merge(guildId, 1, Integer::sum);
            if (failNextDisconnect) {
                failNextDisconnect = false;
                throw new IllegalStateException("boom");
            }
            disconnectedGuilds.add(guildId);
        }

        @Override
        public void close() {
        }

        Set<String> getDisconnectedGuilds() {
            return Set.copyOf(disconnectedGuilds);
        }

        void failNextDisconnect() {
            failNextDisconnect = true;
        }

        int disconnectCallsFor(String guildId) {
            return disconnectCalls.getOrDefault(guildId, 0);
        }

        void reset() {
            disconnectedGuilds.clear();
            disconnectCalls.clear();
            failNextDisconnect = false;
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
            return guilds.computeIfAbsent(guildId, ignored -> new FakeGuild());
        }

        @Override
        public TextChannel textChannel(String channelId) {
            return new TextChannel() {
                @Override
                public void send(String message) {
                }

                @Override
                public SentMessage send(EmbedMessage embed) {
                    return new SentMessage() {
                        @Override
                        public void delete() {
                        }

                        @Override
                        public void update(EmbedMessage updated) {
                        }
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

        private String connectedChannelId;
        private boolean lonely = true;

        @Override
        public Optional<VoiceSession> voice() {
            if (connectedChannelId == null) {
                return Optional.empty();
            }
            return Optional.of(new VoiceSession() {
                @Override
                public String getChannelId() {
                    return connectedChannelId;
                }

                @Override
                public boolean isLonely() {
                    return lonely;
                }

                @Override
                public void leave() {
                    connectedChannelId = null;
                }
            });
        }

        @Override
        public void join(String channelId, AudioProvider provider, VoiceSessionListener onJoined) {
            connectedChannelId = channelId;
            onJoined.onJoined(channelId);
        }

        void simulateJoined(String channelId) {
            connectedChannelId = channelId;
        }

        void setLonely(boolean lonely) {
            this.lonely = lonely;
        }

        void simulateLeft() {
            connectedChannelId = null;
        }
    }
}
