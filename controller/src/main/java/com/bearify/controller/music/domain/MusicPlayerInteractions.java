package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class MusicPlayerInteractions {

    public record Request(String requestId, CompletableFuture<MusicPlayerEvent> future) {}

    private final Map<String, CompletableFuture<MusicPlayerEvent>> pending = new ConcurrentHashMap<>();

    public Request queue() {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<MusicPlayerEvent> future = new CompletableFuture<>();
        pending.put(requestId, future);
        future.whenComplete((e, ex) -> pending.remove(requestId));
        return new Request(requestId, future);
    }

    public boolean complete(String requestId, MusicPlayerEvent event) {
        CompletableFuture<MusicPlayerEvent> future = pending.remove(requestId);
        if (future == null) return false;
        future.complete(event);
        return true;
    }
}
