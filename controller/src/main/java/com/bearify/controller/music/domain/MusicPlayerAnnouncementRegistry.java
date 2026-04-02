package com.bearify.controller.music.domain;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MusicPlayerAnnouncementRegistry {

    private final ConcurrentHashMap<String, Set<PlaybackAnnouncer>> subscriptions = new ConcurrentHashMap<>();

    public void subscribe(String playerId, PlaybackAnnouncer announcer) {
        subscriptions.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet()).add(announcer);
    }

    public void unsubscribe(String playerId, PlaybackAnnouncer announcer) {
        subscriptions.computeIfPresent(playerId, (ignored, announcers) -> {
            announcers.remove(announcer);
            return announcers.isEmpty() ? null : announcers;
        });
    }

    public Collection<PlaybackAnnouncer> findAll(String playerId) {
        return subscriptions.getOrDefault(playerId, Set.of());
    }

    public void removeAll(String playerId) {
        subscriptions.remove(playerId);
    }
}
