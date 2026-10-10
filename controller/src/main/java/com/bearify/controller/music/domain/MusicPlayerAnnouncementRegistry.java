package com.bearify.controller.music.domain;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MusicPlayerAnnouncementRegistry {

    private final ConcurrentHashMap<Key, Set<MusicPlayerEventConsumer>> subscriptions = new ConcurrentHashMap<>();

    public void subscribe(String playerId, String guildId, MusicPlayerEventConsumer announcer) {
        subscriptions.computeIfAbsent(new Key(playerId, guildId), ignored -> ConcurrentHashMap.newKeySet()).add(announcer);
    }

    public Collection<MusicPlayerEventConsumer> findAll(String playerId, String guildId) {
        return subscriptions.getOrDefault(new Key(playerId, guildId), Set.of());
    }

    public void removeAll(String playerId, String guildId) {
        subscriptions.remove(new Key(playerId, guildId));
    }

    private record Key(String playerId, String guildId) {}
}
