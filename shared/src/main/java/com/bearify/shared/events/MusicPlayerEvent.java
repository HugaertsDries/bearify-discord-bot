package com.bearify.shared.events;

import com.bearify.shared.model.Track;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MusicPlayerEvent.TrackStart.class, name = "track_start"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.TrackEnd.class, name = "track_end"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.TrackError.class, name = "track_error"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.QueueEmpty.class, name = "queue_empty"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Ready.class, name = "player_ready"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Stopped.class, name = "player_stopped")
})
public sealed interface MusicPlayerEvent permits
        MusicPlayerEvent.TrackStart,
        MusicPlayerEvent.TrackEnd,
        MusicPlayerEvent.TrackError,
        MusicPlayerEvent.QueueEmpty,
        MusicPlayerEvent.Ready,
        MusicPlayerEvent.Stopped {

    String playerId();
    String requestId();

    record TrackStart(String playerId, String requestId, Track track) implements MusicPlayerEvent {}
    record TrackEnd(String playerId, String requestId, Track track) implements MusicPlayerEvent {}
    record TrackError(String playerId, String requestId, Track track) implements MusicPlayerEvent {}
    record QueueEmpty(String playerId, String requestId) implements MusicPlayerEvent {}
    record Ready(String playerId, String requestId) implements MusicPlayerEvent {}
    record Stopped(String playerId, String requestId) implements MusicPlayerEvent {}
}
