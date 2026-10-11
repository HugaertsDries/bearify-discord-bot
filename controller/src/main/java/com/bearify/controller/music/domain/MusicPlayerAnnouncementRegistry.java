package com.bearify.controller.music.domain;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MusicPlayerAnnouncementRegistry {

    private final ConcurrentHashMap<Session, Set<MusicPlayerEventConsumer>> subscriptions = new ConcurrentHashMap<>();

    public void subscribe(String playerId, String guildId, MusicPlayerEventConsumer announcer) {
        subscriptions.computeIfAbsent(new Session(playerId, guildId), ignored -> ConcurrentHashMap.newKeySet()).add(announcer);
    }

    public Collection<MusicPlayerEventConsumer> findAll(String playerId, String guildId) {
        return subscriptions.getOrDefault(new Session(playerId, guildId), Set.of());
    }

    public void unsubscribe(String playerId, String guildId, MusicPlayerEventConsumer announcer) {
        subscriptions.computeIfPresent(new Session(playerId, guildId), (ignored, announcers) -> {
            announcers.remove(announcer);
            return announcers.isEmpty() ? null : announcers;
        });
    }

    public void removeAll(String playerId, String guildId) {
        subscriptions.remove(new Session(playerId, guildId));
    }

    /** Every bear and guild that currently has announcers. */
    public Set<Session> sessions() {
        return Set.copyOf(subscriptions.keySet());
    }

    public record Session(String playerId, String guildId) {}
}
