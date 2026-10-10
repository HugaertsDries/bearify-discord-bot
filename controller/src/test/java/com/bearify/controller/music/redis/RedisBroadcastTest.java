package com.bearify.controller.music.redis;

import com.bearify.controller.music.domain.MusicPlayerPendingInteractions;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class RedisBroadcastTest {

    @Test
    void rejectsRequestAndDropsPendingEntryWhenPublishFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doThrow(new RedisConnectionFailureException("redis is down")).when(redis).convertAndSend(anyString(), anyString());
        RecordingPendingInteractions pendingInteractions = new RecordingPendingInteractions();
        RedisBroadcast broadcast = new RedisBroadcast(redis, new ObjectMapper(), pendingInteractions);

        assertThatThrownBy(() -> broadcast.request(
                "channel", MusicPlayerInteraction.HealthCheck::new, MusicPlayerEvent.HealthCheckResult.class, Duration.ofSeconds(5)))
                .isInstanceOf(RedisConnectionFailureException.class);

        // a completed future is what removes the entry from the pending map
        assertThat(pendingInteractions.last.get().future()).isCompletedExceptionally();
    }

    private static final class RecordingPendingInteractions extends MusicPlayerPendingInteractions {
        private final AtomicReference<PendingInteraction> last = new AtomicReference<>();

        @Override
        public PendingInteraction register() {
            PendingInteraction pending = super.register();
            last.set(pending);
            return pending;
        }
    }
}
