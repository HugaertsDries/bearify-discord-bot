package com.bearify.controller.music.domain;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MusicPlayerRequestRegistry {

    private final Map<String, MusicPlayerEventHandler> pending = new ConcurrentHashMap<>();

    public void register(String requestId, MusicPlayerEventHandler handler) {
        pending.put(requestId, handler);
    }

    public Optional<MusicPlayerEventHandler> consume(String requestId) {
        return Optional.ofNullable(pending.remove(requestId));
    }
}
