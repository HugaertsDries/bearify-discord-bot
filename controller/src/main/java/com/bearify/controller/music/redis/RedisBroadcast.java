package com.bearify.controller.music.redis;

import com.bearify.controller.music.domain.MusicPlayerPendingInteractions;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Publishes an interaction on a channel, one bear's or every bear's, and waits for the first reply to it. */
final class RedisBroadcast {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MusicPlayerPendingInteractions pendingInteractions;

    RedisBroadcast(StringRedisTemplate redis, ObjectMapper objectMapper, MusicPlayerPendingInteractions pendingInteractions) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.pendingInteractions = pendingInteractions;
    }

    /** Returns the first reply of {@code replyType}; empty when the reply has another type or doesn't come within {@code timeout}. */
    <T extends MusicPlayerEvent> Optional<T> request(String channel,
                                                     Function<String, MusicPlayerInteraction> interactionForRequestId,
                                                     Class<T> replyType,
                                                     Duration timeout) {
        MusicPlayerPendingInteractions.PendingInteraction pending = pendingInteractions.register();
        try {
            redis.convertAndSend(channel, serialize(interactionForRequestId.apply(pending.requestId())));
        } catch (RuntimeException e) {
            // completing the future drops the pending entry, which would otherwise never be answered
            pending.future().completeExceptionally(e);
            throw e;
        }
        return pending.future()
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .thenApply(event -> replyType.isInstance(event) ? Optional.of(replyType.cast(event)) : Optional.<T>empty())
                .exceptionally(ex -> Optional.empty())
                .join();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize interaction", e);
        }
    }
}
