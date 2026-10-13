package com.bearify.music.player.agent.domain;

import com.bearify.discord.api.gateway.Activity;
import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.DiscordClientFactory;
import com.bearify.discord.api.gateway.Guild;
import com.bearify.discord.api.gateway.SentMessage;
import com.bearify.discord.api.gateway.TextChannel;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.Interaction;
import com.bearify.discord.api.message.ComponentMessage;
import com.bearify.discord.api.model.CommandDefinition;
import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.discord.api.voice.VoiceSessionListener;
import com.bearify.music.player.agent.config.PlayerProperties;
import com.bearify.music.player.agent.domain.VoiceSessionHeartbeatTest.TestConfig;
import com.bearify.music.player.bridge.events.JoinRequest;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.model.Request;
import com.bearify.music.player.bridge.model.StopReason;
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
        "music-player.voice-session.nobody-listening-timeout=5m",
        "music-player.voice-session.idle-timeout=10m",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.task.scheduling.enabled=false"
})
@Import(TestConfig.class)
class VoiceSessionHeartbeatTest {

    private static final String GUILD_ID = "guild-1";
    private static final String CHANNEL_ID = "voice-1";
    private static final String OTHER_CHANNEL_ID = "voice-2";
    private static final String PLAYER_ID = "test-player";
    private static final String OTHER_PLAYER_ID = "other-player";

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
    @Autowired PlayerProperties properties;

    private final AudioPlayerTest.InMemoryAudioEngine engine = new AudioPlayerTest.InMemoryAudioEngine();
    private final AudioPlayerTest.RecordingEventDispatcher events = new AudioPlayerTest.RecordingEventDispatcher();

    @AfterEach
    void cleanup() {
        recordingManager.reset();
        discordClient.reset();
        clock.reset();
        ((Map<?, ?>) ReflectionTestUtils.getField(pool, "entries")).clear();
        ((Map<?, ?>) ReflectionTestUtils.getField(heartbeat, "nobodyListeningSince")).clear();
        ((Map<?, ?>) ReflectionTestUtils.getField(heartbeat, "idleSince")).clear();
        ((Map<?, ?>) ReflectionTestUtils.getField(heartbeat, "connectedChannels")).clear();
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, CHANNEL_ID));
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, OTHER_CHANNEL_ID));
        redis.delete(PlayerRedisProtocol.Keys.connectRequest("req-3"));
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
    void doesNotDisconnectBeforeNobodyListeningTimeoutExpires() {
        pool.getOrCreate(GUILD_ID);
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);

        heartbeat.maintain();
        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        heartbeat.maintain();

        assertThat(recordingManager.getDisconnectedGuilds()).isEmpty();
    }

    @Test
    void disconnectsWithNobodyListeningReasonAfterTimeout() {
        pool.getOrCreate(GUILD_ID);
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);

        heartbeat.maintain();
        clock.advance(Duration.ofMinutes(5));
        heartbeat.maintain();

        assertThat(recordingManager.stopReason(GUILD_ID)).contains(StopReason.NOBODY_LISTENING);
    }

    @Test
    void autoPausesWhenNobodyIsListening() {
        playingPlayer();
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);

        heartbeat.maintain();

        assertThat(engine.isPaused()).isTrue();
        assertThat(events.getEvents()).anyMatch(MusicPlayerEvent.AutoPaused.class::isInstance);
    }

    @Test
    void keepsPlayingWhileSomeoneIsListening() {
        playingPlayer();
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);
        discordClient.guild(GUILD_ID).setListeners(true);

        heartbeat.maintain();

        assertThat(engine.isPaused()).isFalse();
    }

    @Test
    void autoResumesWhenListenerReturns() {
        playingPlayer();
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);
        heartbeat.maintain();

        discordClient.guild(GUILD_ID).setListeners(true);
        heartbeat.maintain();

        assertThat(engine.isPaused()).isFalse();
        assertThat(events.getLastEvent()).isInstanceOf(MusicPlayerEvent.AutoResumed.class);
    }

    @Test
    void keepsHumanPauseWhenListenerReturns() {
        AudioPlayer player = playingPlayer();
        player.togglePause(new Request("req-1", "@user"));
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);
        heartbeat.maintain();

        discordClient.guild(GUILD_ID).setListeners(true);
        heartbeat.maintain();

        assertThat(engine.isPaused()).isTrue();
        assertThat(events.getEvents()).noneMatch(MusicPlayerEvent.AutoResumed.class::isInstance);
    }

    @Test
    void disconnectsWithIdleReasonAfterIdleTimeout() {
        pool.getOrCreate(GUILD_ID);
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);
        discordClient.guild(GUILD_ID).setListeners(true);

        heartbeat.maintain();
        clock.advance(Duration.ofMinutes(9).plusSeconds(59));
        heartbeat.maintain();
        assertThat(recordingManager.getDisconnectedGuilds()).isEmpty();

        clock.advance(Duration.ofSeconds(1));
        heartbeat.maintain();

        assertThat(recordingManager.stopReason(GUILD_ID)).contains(StopReason.IDLE);
    }

    @Test
    void disconnectsWithIdleReasonWhenHumanPauseLasts() {
        AudioPlayer player = playingPlayer();
        player.togglePause(new Request("req-1", "@user"));
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);
        discordClient.guild(GUILD_ID).setListeners(true);

        heartbeat.maintain();
        clock.advance(Duration.ofMinutes(10));
        heartbeat.maintain();

        assertThat(recordingManager.stopReason(GUILD_ID)).contains(StopReason.IDLE);
    }

    @Test
    void startsIdleTimerAfreshForPlayerCreatedAfterLeave() {
        pool.getOrCreate(GUILD_ID);
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);
        discordClient.guild(GUILD_ID).setListeners(true);
        heartbeat.maintain();

        pool.get(GUILD_ID).orElseThrow().close();
        clock.advance(Duration.ofMinutes(6));
        heartbeat.maintain();
        clock.advance(Duration.ofMinutes(6));
        pool.getOrCreate(GUILD_ID);
        heartbeat.maintain();

        assertThat(recordingManager.getDisconnectedGuilds()).isEmpty();
    }

    @Test
    void leavesWithNobodyListeningReasonWhenListenersLeaveAHumanPause() {
        AudioPlayer player = playingPlayer();
        player.togglePause(new Request("req-1", "@user"));
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);
        discordClient.guild(GUILD_ID).setListeners(true);
        heartbeat.maintain();

        clock.advance(Duration.ofMinutes(8));
        discordClient.guild(GUILD_ID).setListeners(false);
        heartbeat.maintain();
        clock.advance(Duration.ofMinutes(3));
        heartbeat.maintain();
        assertThat(recordingManager.getDisconnectedGuilds()).isEmpty();

        clock.advance(Duration.ofMinutes(2));
        heartbeat.maintain();

        assertThat(recordingManager.stopReason(GUILD_ID)).contains(StopReason.NOBODY_LISTENING);
    }

    @Test
    void resetsIdleTimerWhenPlaybackResumes() {
        AudioPlayer player = playingPlayer();
        player.togglePause(new Request("req-1", "@user"));
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);
        discordClient.guild(GUILD_ID).setListeners(true);

        heartbeat.maintain();
        clock.advance(Duration.ofMinutes(6));
        player.togglePause(new Request("req-2", "@user"));
        heartbeat.maintain();
        player.togglePause(new Request("req-3", "@user"));
        heartbeat.maintain();
        clock.advance(Duration.ofMinutes(6));
        heartbeat.maintain();

        assertThat(recordingManager.getDisconnectedGuilds()).isEmpty();
    }

    @Test
    void clearsNobodyListeningTrackingWhenListenerReturns() {
        pool.getOrCreate(GUILD_ID);
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);

        heartbeat.maintain();
        clock.advance(Duration.ofMinutes(4));
        discordClient.guild(GUILD_ID).setListeners(true);
        heartbeat.maintain();
        clock.advance(Duration.ofMinutes(4));
        discordClient.guild(GUILD_ID).setListeners(false);
        heartbeat.maintain();

        assertThat(recordingManager.getDisconnectedGuilds()).isEmpty();
    }

    @Test
    void stopsPlayerWhenBearWasDisconnectedFromVoice() {
        pool.getOrCreate(GUILD_ID);
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);
        heartbeat.maintain();

        discordClient.guild(GUILD_ID).simulateLeft();
        heartbeat.maintain();
        heartbeat.maintain();

        assertThat(recordingManager.stopReason(GUILD_ID)).contains(StopReason.DISCONNECTED);
        assertThat(recordingManager.disconnectCallsFor(GUILD_ID)).isEqualTo(1);
    }

    @Test
    void closesKickedBearsPlayerAndStopsItOnce() {
        VoiceConnectionManager manager = realManager();
        VoiceSessionHeartbeat realHeartbeat = heartbeatWith(manager);
        manager.connect(new MusicPlayerInteraction.Connect(PLAYER_ID, "req-1", CHANNEL_ID, GUILD_ID));
        realHeartbeat.maintain();

        discordClient.guild(GUILD_ID).simulateLeft();
        realHeartbeat.maintain();
        realHeartbeat.maintain();

        assertThat(pool.get(GUILD_ID)).isEmpty();
        assertThat(redis.hasKey(PlayerRedisProtocol.Keys.assignment(GUILD_ID, CHANNEL_ID))).isFalse();
        assertThat(stopReasons()).containsExactly(StopReason.DISCONNECTED);
    }

    @Test
    void keepsNewPlayerWhenStopAndRejoinFallWithinOneTick() {
        VoiceConnectionManager manager = realManager();
        VoiceSessionHeartbeat realHeartbeat = heartbeatWith(manager);
        manager.connect(new MusicPlayerInteraction.Connect(PLAYER_ID, "req-1", CHANNEL_ID, GUILD_ID));
        realHeartbeat.maintain();

        manager.disconnect(GUILD_ID, "req-2", StopReason.REQUESTED);
        discordClient.guild(GUILD_ID).deferJoins();
        redis.opsForValue().set(PlayerRedisProtocol.Keys.connectRequest("req-3"), "1");
        manager.claim(new JoinRequest("req-3", GUILD_ID, CHANNEL_ID));
        AudioPlayer fresh = pool.get(GUILD_ID).orElseThrow();
        realHeartbeat.maintain();
        discordClient.guild(GUILD_ID).completeJoin();
        realHeartbeat.maintain();

        assertThat(pool.get(GUILD_ID)).containsSame(fresh);
        assertThat(stopReasons()).containsExactly(StopReason.REQUESTED);
    }

    @Test
    void keepsPlayerWhenKickedBearIsJoinedAgainMidTick() {
        VoiceConnectionManager manager = realManager();
        VoiceSessionHeartbeat realHeartbeat = heartbeatWith(manager);
        manager.connect(new MusicPlayerInteraction.Connect(PLAYER_ID, "req-1", CHANNEL_ID, GUILD_ID));
        AudioPlayer player = pool.get(GUILD_ID).orElseThrow();
        realHeartbeat.maintain();

        discordClient.guild(GUILD_ID).simulateLeft();
        discordClient.guild(GUILD_ID).deferJoins();
        manager.connect(new MusicPlayerInteraction.Connect(PLAYER_ID, "req-2", CHANNEL_ID, GUILD_ID));
        realHeartbeat.maintain();
        discordClient.guild(GUILD_ID).completeJoin();
        realHeartbeat.maintain();

        assertThat(pool.get(GUILD_ID)).containsSame(player);
        assertThat(stopReasons()).isEmpty();
    }

    @Test
    void retriesStopOnLaterTickWhenDisconnectFailsAfterBearWasSentOut() {
        pool.getOrCreate(GUILD_ID);
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);
        heartbeat.maintain();
        discordClient.guild(GUILD_ID).simulateLeft();
        recordingManager.failNextDisconnect();

        heartbeat.maintain();
        heartbeat.maintain();

        assertThat(recordingManager.disconnectCallsFor(GUILD_ID)).isEqualTo(2);
        assertThat(recordingManager.stopReason(GUILD_ID)).contains(StopReason.DISCONNECTED);
    }

    @Test
    void keepsPlayerWhileJoinHasNotCompleted() {
        pool.getOrCreate(GUILD_ID);

        heartbeat.maintain();
        heartbeat.maintain();

        assertThat(recordingManager.getDisconnectedGuilds()).isEmpty();
        assertThat(pool.get(GUILD_ID)).isPresent();
    }

    @Test
    void reassignsChannelWhenBearWasMoved() {
        pool.getOrCreate(GUILD_ID);
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, CHANNEL_ID), PLAYER_ID);
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);
        heartbeat.maintain();

        discordClient.guild(GUILD_ID).simulateJoined(OTHER_CHANNEL_ID);
        heartbeat.maintain();

        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID, OTHER_CHANNEL_ID))).isEqualTo(PLAYER_ID);
        assertThat(redis.getExpire(PlayerRedisProtocol.Keys.assignment(GUILD_ID, OTHER_CHANNEL_ID))).isGreaterThan(0);
        assertThat(redis.hasKey(PlayerRedisProtocol.Keys.assignment(GUILD_ID, CHANNEL_ID))).isFalse();
        assertThat(pool.get(GUILD_ID)).isPresent();
        assertThat(recordingManager.getDisconnectedGuilds()).isEmpty();
    }

    @Test
    void recreatesAssignmentWhenKeyIsMissing() {
        pool.getOrCreate(GUILD_ID);
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);

        heartbeat.maintain();

        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID, CHANNEL_ID))).isEqualTo(PLAYER_ID);
        assertThat(redis.getExpire(PlayerRedisProtocol.Keys.assignment(GUILD_ID, CHANNEL_ID))).isGreaterThan(0);
    }

    @Test
    void leavesOtherAgentsAssignmentUntouched() {
        pool.getOrCreate(GUILD_ID);
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, CHANNEL_ID), OTHER_PLAYER_ID);
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, OTHER_CHANNEL_ID), OTHER_PLAYER_ID);
        discordClient.guild(GUILD_ID).simulateJoined(CHANNEL_ID);
        heartbeat.maintain();

        discordClient.guild(GUILD_ID).simulateJoined(OTHER_CHANNEL_ID);
        heartbeat.maintain();
        discordClient.guild(GUILD_ID).simulateLeft();
        heartbeat.maintain();

        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID, CHANNEL_ID))).isEqualTo(OTHER_PLAYER_ID);
        assertThat(redis.opsForValue().get(PlayerRedisProtocol.Keys.assignment(GUILD_ID, OTHER_CHANNEL_ID))).isEqualTo(OTHER_PLAYER_ID);
    }

    @Test
    void resetsTrackingWhenChannelChanges() {
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

    private VoiceConnectionManager realManager() {
        return new VoiceConnectionManager(discordClient, events, pool, redis, properties, PLAYER_ID, clock);
    }

    private VoiceSessionHeartbeat heartbeatWith(VoiceConnectionManager manager) {
        return new VoiceSessionHeartbeat(pool, discordClient, redis, properties, manager, clock, PLAYER_ID);
    }

    private List<StopReason> stopReasons() {
        return events.getEvents().stream()
                .filter(MusicPlayerEvent.Stopped.class::isInstance)
                .map(event -> ((MusicPlayerEvent.Stopped) event).reason())
                .toList();
    }

    private AudioPlayer playingPlayer() {
        AudioPlayer player = new AudioPlayer(engine, new AudioPlayerTest.NoOpAudioProvider(), events, properties,
                null, "test-player", GUILD_ID, new VoiceChannelStatus(discordClient, GUILD_ID), () -> {});
        @SuppressWarnings("unchecked")
        Map<String, AudioPlayer> entries = (Map<String, AudioPlayer>) ReflectionTestUtils.getField(pool, "entries");
        entries.put(GUILD_ID, player);
        engine.play(new AudioPlayerTest.InMemoryTrack("Song A", 120_000, "@alice"));
        return player;
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
        private final Map<String, StopReason> stopReasons = new HashMap<>();
        private boolean failNextDisconnect;

        TestVoiceConnectionManager() {
            super(null, null, null, null, null, "test", null);
        }

        @Override
        public void disconnect(String guildId, StopReason reason) {
            disconnectCalls.merge(guildId, 1, Integer::sum);
            if (failNextDisconnect) {
                failNextDisconnect = false;
                throw new IllegalStateException("boom");
            }
            disconnectedGuilds.add(guildId);
            stopReasons.put(guildId, reason);
        }

        @Override
        public void stopSentOut(String guildId, String channelId, AudioPlayer player) {
            disconnect(guildId, StopReason.DISCONNECTED);
        }

        @Override
        public void close() {
        }

        Set<String> getDisconnectedGuilds() {
            return Set.copyOf(disconnectedGuilds);
        }

        Optional<StopReason> stopReason(String guildId) {
            return Optional.ofNullable(stopReasons.get(guildId));
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
            stopReasons.clear();
            failNextDisconnect = false;
        }
    }

    static final class FakeDiscordClientFactory implements DiscordClientFactory {

        private final FakeDiscordClient client;

        private FakeDiscordClientFactory(FakeDiscordClient client) {
            this.client = client;
        }

        @Override
        public DiscordClient create(List<CommandDefinition> commands, Consumer<Interaction> handler, Optional<Activity> activity, Optional<Duration> ephemeralDeleteAfter) {
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
        public String selfUserId() {
            return "test-bot-user";
        }

        @Override
        public TextChannel textChannel(String channelId) {
            return new TextChannel() {
                @Override
                public void send(String message) {
                }

                @Override
                public SentMessage send(ComponentMessage message) {
                    return null;
                }

                @Override
                public void delete(String messageId) {
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
        private boolean listeners = false;
        private boolean deferJoins;
        private Runnable pendingJoin = () -> {};

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
                    return !listeners;
                }

                @Override
                public boolean hasListeners() {
                    return listeners;
                }

                @Override
                public boolean setStatus(String status) {
                    return true;
                }

                @Override
                public void leave() {
                    connectedChannelId = null;
                }
            });
        }

        @Override
        public boolean isInVoice(String userId) {
            return false;
        }

        @Override
        public void join(String channelId, AudioProvider provider, VoiceSessionListener onJoined) {
            pendingJoin = () -> {
                connectedChannelId = channelId;
                onJoined.onJoined(channelId);
            };
            if (!deferJoins) {
                pendingJoin.run();
            }
        }

        // JDA joins asynchronously: until then voice() stays empty
        void deferJoins() {
            deferJoins = true;
        }

        void completeJoin() {
            deferJoins = false;
            pendingJoin.run();
        }

        void simulateJoined(String channelId) {
            connectedChannelId = channelId;
        }

        void setListeners(boolean listeners) {
            this.listeners = listeners;
        }

        void simulateLeft() {
            connectedChannelId = null;
        }
    }
}
