package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class MusicPlayerPendingRequests {

    public record Pending(String requestId, CompletableFuture<MusicPlayerEvent> future) {}

    private final Map<String, CompletableFuture<MusicPlayerEvent>> pending = new ConcurrentHashMap<>();

    public Pending register() {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<MusicPlayerEvent> future = new CompletableFuture<>();
        pending.put(requestId, future);
        return new Pending(requestId, future);
    }

    public boolean complete(String requestId, MusicPlayerEvent event) {
        CompletableFuture<MusicPlayerEvent> future = pending.remove(requestId);
        if (future == null) return false;
        future.complete(event);
        return true;
    }
}
