package com.bearify.controller.music.redis;

import com.bearify.controller.AbstractControllerIntegrationTest;
import com.bearify.controller.music.domain.MusicPlayerAnnouncementConsumer;
import com.bearify.controller.music.domain.MusicPlayerAnnouncementRegistry;
import com.bearify.discord.testing.MockDiscordClient;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.StopReason;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class PlayerLivenessMonitorTest extends AbstractControllerIntegrationTest {

    private static final String PLAYER_ID = "player-liveness";
    private static final String OTHER_PLAYER_ID = "player-other";
    private static final String GUILD_ID = "guild-liveness";
    private static final String VOICE_CHANNEL_ID = "voice-1";
    private static final String OTHER_VOICE_CHANNEL_ID = "voice-2";
    private static final String BOT_USER_ID = "bear-discord-user";
    // the agent's music-player.assignment.ttl default
    private static final Duration ASSIGNMENT_TTL = Duration.ofSeconds(30);

    @Autowired StringRedisTemplate redis;

    private MusicPlayerAnnouncementRegistry registry;
    private MockDiscordClient discord;
    private List<MusicPlayerEvent> delivered;
    private PlayerLivenessMonitor monitor;

    @BeforeEach
    void setUp() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, OTHER_VOICE_CHANNEL_ID));
        redis.opsForValue().set(PlayerRedisProtocol.Keys.bot(PLAYER_ID), BOT_USER_ID);
        discord = new MockDiscordClient.Factory().create(List.of(), interaction -> {}, Optional.empty());
        registry = new MusicPlayerAnnouncementRegistry();
        delivered = new CopyOnWriteArrayList<>();
        registry.subscribe(PLAYER_ID, GUILD_ID, delivered::add);
        monitor = new PlayerLivenessMonitor(
                registry,
                new RedisPlayerAssignments(redis),
                new MusicPlayerAnnouncementConsumer(registry),
                discord,
                mock(TaskScheduler.class),
                new LivenessProperties(Duration.ofSeconds(15)),
                Clock.systemUTC());
    }

    @AfterEach
    void tearDown() {
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, OTHER_VOICE_CHANNEL_ID));
        redis.delete(PlayerRedisProtocol.Keys.bot(PLAYER_ID));
    }

    @Test
    void reportsBearLostAfterTwoTicksWithoutAssignment() {
        // another bear holding the channel doesn't count for this one
        assign(OTHER_PLAYER_ID);

        monitor.tick();
        monitor.tick();

        assertThat(delivered).singleElement().isInstanceOfSatisfying(MusicPlayerEvent.Stopped.class,
                stopped -> assertThat(stopped.reason()).isEqualTo(StopReason.LOST));
        assertThat(registry.sessions()).isEmpty();
    }

    @Test
    void ignoresAssignmentMissingForOneTick() {
        monitor.tick();
        assign(PLAYER_ID);
        monitor.tick();
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        monitor.tick();

        assertThat(delivered).isEmpty();
        assertThat(registry.findAll(PLAYER_ID, GUILD_ID)).hasSize(1);
    }

    @Test
    void keepsSessionWhoseBearIsStillAssigned() {
        assign(PLAYER_ID);

        monitor.tick();
        monitor.tick();
        monitor.tick();

        assertThat(delivered).isEmpty();
        assertThat(registry.findAll(PLAYER_ID, GUILD_ID)).hasSize(1);
    }

    @Test
    void keepsBearDiscordStillShowsInVoiceAfterItsNoteExpired() {
        discord.setInVoice(GUILD_ID, BOT_USER_ID, true);

        monitor.tick();
        monitor.tick();
        monitor.tick();

        assertThat(delivered).isEmpty();
        assertThat(registry.findAll(PLAYER_ID, GUILD_ID)).hasSize(1);
    }

    @Test
    void declaresBearLostOnceDiscordNoLongerShowsItInVoice() {
        discord.setInVoice(GUILD_ID, BOT_USER_ID, true);
        monitor.tick();
        monitor.tick();

        discord.setInVoice(GUILD_ID, BOT_USER_ID, false);
        monitor.tick();

        assertThat(delivered).singleElement().isInstanceOfSatisfying(MusicPlayerEvent.Stopped.class,
                stopped -> assertThat(stopped.reason()).isEqualTo(StopReason.LOST));
        assertThat(registry.sessions()).isEmpty();
    }

    @Test
    void keepsBearWhenItsDiscordIdIsUnknown() {
        redis.delete(PlayerRedisProtocol.Keys.bot(PLAYER_ID));

        monitor.tick();
        monitor.tick();
        monitor.tick();

        assertThat(delivered).isEmpty();
        assertThat(registry.findAll(PLAYER_ID, GUILD_ID)).hasSize(1);
    }

    @Test
    void rejectsNonPositiveInterval() {
        assertThatThrownBy(() -> new LivenessProperties(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bearify.liveness.interval must be greater than 0");
    }

    private void assign(String playerId) {
        redis.opsForValue().set(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID), playerId);
    }


    @Test
    void schedulesFirstTickOneIntervalAfterStartAndThenEveryInterval() throws NoSuchMethodException {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        Instant now = Instant.parse("2026-10-11T12:00:00Z");
        Duration interval = Duration.ofSeconds(15);
        PlayerLivenessMonitor scheduled = new PlayerLivenessMonitor(
                registry,
                new RedisPlayerAssignments(redis),
                new MusicPlayerAnnouncementConsumer(registry),
                discord,
                scheduler,
                new LivenessProperties(interval),
                Clock.fixed(now, ZoneOffset.UTC));

        scheduled.start();

        verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(now.plus(interval)), eq(interval));
        assertThat(PlayerLivenessMonitor.class.getMethod("start").getAnnotation(EventListener.class).value())
                .containsExactly(ApplicationReadyEvent.class);
    }

    @Test
    void keepsBearThatWasDraggedToAnotherChannel() {
        heartbeatKeepsAssignment(VOICE_CHANNEL_ID);
        monitor.tick();

        // the heartbeat that notices the drag first drops the old channel's key, and a tick can fall in between
        heartbeatDropsOwnAssignment(VOICE_CHANNEL_ID);
        monitor.tick();
        heartbeatKeepsAssignment(OTHER_VOICE_CHANNEL_ID);
        monitor.tick();
        heartbeatKeepsAssignment(OTHER_VOICE_CHANNEL_ID);
        monitor.tick();

        assertThat(delivered).isEmpty();
        assertThat(registry.findAll(PLAYER_ID, GUILD_ID)).hasSize(1);
    }

    @Test
    void keepsBearWhoseLostAssignmentTheHeartbeatRecreated() {
        heartbeatKeepsAssignment(VOICE_CHANNEL_ID);
        monitor.tick();

        // Redis lost the key (flush, failover); the next heartbeat recreates it before a second tick misses it
        redis.delete(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID));
        monitor.tick();
        heartbeatKeepsAssignment(VOICE_CHANNEL_ID);
        monitor.tick();
        heartbeatKeepsAssignment(VOICE_CHANNEL_ID);
        monitor.tick();

        assertThat(delivered).isEmpty();
        assertThat(redis.getExpire(PlayerRedisProtocol.Keys.assignment(GUILD_ID, VOICE_CHANNEL_ID))).isPositive();
    }

    // the same writes as the agent's VoiceSessionHeartbeat.keepAssignment: refresh the TTL, or recreate the key when it's gone
    private void heartbeatKeepsAssignment(String voiceChannelId) {
        String key = PlayerRedisProtocol.Keys.assignment(GUILD_ID, voiceChannelId);
        if (!Boolean.TRUE.equals(redis.expire(key, ASSIGNMENT_TTL))) {
            redis.opsForValue().setIfAbsent(key, PLAYER_ID, ASSIGNMENT_TTL);
        }
    }

    // the same write as the agent's VoiceSessionHeartbeat.deleteIfOurs when the bear moved away from this channel
    private void heartbeatDropsOwnAssignment(String voiceChannelId) {
        String key = PlayerRedisProtocol.Keys.assignment(GUILD_ID, voiceChannelId);
        if (PLAYER_ID.equals(redis.opsForValue().get(key))) {
            redis.delete(key);
        }
    }
}
