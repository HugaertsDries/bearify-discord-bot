package com.bearify.shared.events;

import com.bearify.shared.model.Track;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PlayerEvent.TrackStart.class, name = "track_start"),
        @JsonSubTypes.Type(value = PlayerEvent.TrackEnd.class, name = "track_end"),
        @JsonSubTypes.Type(value = PlayerEvent.TrackError.class, name = "track_error"),
        @JsonSubTypes.Type(value = PlayerEvent.QueueEmpty.class, name = "queue_empty"),
        @JsonSubTypes.Type(value = PlayerEvent.PlayerReady.class, name = "player_ready"),
        @JsonSubTypes.Type(value = PlayerEvent.PlayerStopped.class, name = "player_stopped")
})
public sealed interface PlayerEvent permits
        PlayerEvent.TrackStart,
        PlayerEvent.TrackEnd,
        PlayerEvent.TrackError,
        PlayerEvent.QueueEmpty,
        PlayerEvent.PlayerReady,
        PlayerEvent.PlayerStopped {

    String playerId();
    String requestId();

    record TrackStart(String playerId, String requestId, Track track) implements PlayerEvent {}
    record TrackEnd(String playerId, String requestId, Track track) implements PlayerEvent {}
    record TrackError(String playerId, String requestId, Track track) implements PlayerEvent {}
    record QueueEmpty(String playerId, String requestId) implements PlayerEvent {}
    record PlayerReady(String playerId, String requestId) implements PlayerEvent {}
    record PlayerStopped(String playerId, String requestId) implements PlayerEvent {}
}
