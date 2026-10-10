package com.bearify.controller.music.redis;

import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerAnnouncementRegistry;
import com.bearify.controller.music.domain.MusicPlayerEventListener;
import com.bearify.controller.music.domain.MusicPlayerPendingInteractions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** How {@link RedisMusicPlayer} answers when Redis or the agent can't: without a running Redis. */
class RedisMusicPlayerAnswersTest {

    private static final String PLAYER_ID = "player-1";
    private static final String GUILD_ID = "guild-1";
    private static final String VOICE_CHANNEL_ID = "voice-1";
    private static final String REQUESTER_TAG = "<@user-1>";
    private static final Duration TIMEOUT = Duration.ofMillis(300);

    @Test
    void answersFailureWhenRequestCannotBeSent() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.convertAndSend(anyString(), any())).thenThrow(new RedisConnectionFailureException("Redis is down"));
        AtomicBoolean failed = new AtomicBoolean(false);
        AtomicBoolean timedOut = new AtomicBoolean(false);

        player(Optional.of(PLAYER_ID), redis).clear(REQUESTER_TAG, new MusicPlayerEventListener() {
            @Override public void onFailed(String reason) { failed.set(true); }
            @Override public void onTimedOut() { timedOut.set(true); }
        });

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(failed.get()).isTrue());
        // the failed send completed the request, so its timeout never fires
        await().during(2 * TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).atMost(1, TimeUnit.SECONDS).until(() -> !timedOut.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    void answersFailureWhenJoinRequestCannotBeSent() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.convertAndSend(anyString(), any())).thenThrow(new RedisConnectionFailureException("Redis is down"));
        AtomicBoolean failed = new AtomicBoolean(false);

        player(Optional.empty(), redis).join(new MusicPlayerEventListener() {
            @Override public void onFailed(String reason) { failed.set(true); }
        });

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(failed.get()).isTrue());
    }

    @Test
    void answersFailureWhenPlayerIsNotReadyYet() {
        AtomicBoolean failed = new AtomicBoolean(false);

        player(Optional.empty(), mock(StringRedisTemplate.class)).clear(REQUESTER_TAG, new MusicPlayerEventListener() {
            @Override public void onFailed(String reason) { failed.set(true); }
        });

        assertThat(failed.get()).isTrue();
    }

    @Test
    void answersTimeoutsOffTheTimeoutThread() {
        AtomicReference<Thread> answeredOn = new AtomicReference<>();

        player(Optional.of(PLAYER_ID), mock(StringRedisTemplate.class)).togglePause(REQUESTER_TAG, new MusicPlayerEventListener() {
            @Override public void onTimedOut() { answeredOn.set(Thread.currentThread()); }
        });

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(answeredOn.get()).isNotNull());
        assertThat(answeredOn.get().isVirtual()).isTrue();
    }

    private static MusicPlayer player(Optional<String> playerId, StringRedisTemplate redis) {
        return new RedisMusicPlayer(playerId, GUILD_ID, VOICE_CHANNEL_ID, redis, new ObjectMapper(),
                new MusicPlayerPendingInteractions(), new MusicPlayerAnnouncementRegistry(), null,
                new MusicPlayerPoolProperties(TIMEOUT, TIMEOUT, TIMEOUT));
    }
}
