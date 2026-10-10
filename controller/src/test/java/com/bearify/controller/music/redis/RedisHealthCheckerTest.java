package com.bearify.controller.music.redis;

import com.bearify.controller.music.domain.MusicPlayerPendingInteractions;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.protocol.PlayerRedisProtocol;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class RedisHealthCheckerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void broadcastsHealthCheckAndReturnsFirstResult() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        MusicPlayerPendingInteractions pendingInteractions = new MusicPlayerPendingInteractions();
        doAnswer(invocation -> {
            assertThat(invocation.getArgument(0, String.class)).isEqualTo(PlayerRedisProtocol.Channels.HEALTH);
            MusicPlayerInteraction.HealthCheck check = (MusicPlayerInteraction.HealthCheck) OBJECT_MAPPER.readValue(
                    invocation.getArgument(1, String.class), MusicPlayerInteraction.class);
            // two bears answer: the first one wins
            pendingInteractions.accept(new MusicPlayerEvent.HealthCheckResult("player-1", check.requestId(), false, Optional.of("broken")));
            pendingInteractions.accept(new MusicPlayerEvent.HealthCheckResult("player-2", check.requestId(), true, Optional.empty()));
            return null;
        }).when(redis).convertAndSend(anyString(), anyString());

        RedisHealthChecker checker = new RedisHealthChecker(redis, OBJECT_MAPPER, pendingInteractions, Duration.ofSeconds(5));

        assertThat(checker.check()).hasValueSatisfying(result -> {
            assertThat(result.playerId()).isEqualTo("player-1");
            assertThat(result.ok()).isFalse();
            assertThat(result.reason()).contains("broken");
        });
    }

    @Test
    void returnsEmptyWhenNoBearAnswers() {
        RedisHealthChecker checker = new RedisHealthChecker(
                mock(StringRedisTemplate.class), OBJECT_MAPPER, new MusicPlayerPendingInteractions(), Duration.ofMillis(100));

        Optional<MusicPlayerEvent.HealthCheckResult> result = assertTimeoutPreemptively(Duration.ofSeconds(2), checker::check);

        assertThat(result).isEmpty();
    }
}
