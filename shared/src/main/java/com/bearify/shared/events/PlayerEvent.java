package com.bearify.shared.events;

import com.bearify.shared.model.Track;

public sealed interface PlayerEvent permits
        PlayerEvent.TrackStart,
        PlayerEvent.TrackEnd,
        PlayerEvent.TrackError,
        PlayerEvent.QueueEmpty,
        PlayerEvent.PlayerReady,
        PlayerEvent.PlayerStopped {

    String playerId();

    record TrackStart(String playerId, Track track) implements PlayerEvent {}
    record TrackEnd(String playerId, Track track) implements PlayerEvent {}
    record TrackError(String playerId, Track track) implements PlayerEvent {}
    record QueueEmpty(String playerId) implements PlayerEvent {}
    record PlayerReady(String playerId) implements PlayerEvent {}
    record PlayerStopped(String playerId) implements PlayerEvent {}
}
