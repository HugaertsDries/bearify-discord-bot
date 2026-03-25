package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class MusicPlayerPendingInteractions implements MusicPlayerEventConsumer {

    public record PendingInteraction(String requestId, CompletableFuture<MusicPlayerEvent> future) {}

    private final Map<String, CompletableFuture<MusicPlayerEvent>> pending = new ConcurrentHashMap<>();

    public PendingInteraction register() {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<MusicPlayerEvent> future = new CompletableFuture<>();
        pending.put(requestId, future);
        future.whenComplete((event, ex) -> pending.remove(requestId));
        return new PendingInteraction(requestId, future);
    }

    @Override
    public void accept(MusicPlayerEvent event) {
        CompletableFuture<MusicPlayerEvent> future = pending.remove(event.requestId());
        if (future != null) {
            future.complete(event);
        }
    }
}
