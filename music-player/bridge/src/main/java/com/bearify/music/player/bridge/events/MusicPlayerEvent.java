package com.bearify.music.player.bridge.events;

import com.bearify.music.player.bridge.model.TrackMetadata;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MusicPlayerEvent.TrackStart.class, name = "track_start"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.TrackEnd.class, name = "track_end"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.TrackError.class, name = "track_error"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.QueueEmpty.class, name = "queue_empty"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Paused.class, name = "paused"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Resumed.class, name = "resumed"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.NothingToGoBack.class, name = "nothing_to_go_back"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.TrackNotFound.class, name = "track_not_found"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.TrackLoadFailed.class, name = "track_load_failed"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Ready.class, name = "player_ready"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Stopped.class, name = "player_stopped"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.ConnectFailed.class, name = "connect_failed"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.PlayerNotFound.class, name = "player_not_found")
})
public sealed interface MusicPlayerEvent permits
        MusicPlayerEvent.TrackStart,
        MusicPlayerEvent.TrackEnd,
        MusicPlayerEvent.TrackError,
        MusicPlayerEvent.QueueEmpty,
        MusicPlayerEvent.Paused,
        MusicPlayerEvent.Resumed,
        MusicPlayerEvent.NothingToGoBack,
        MusicPlayerEvent.TrackNotFound,
        MusicPlayerEvent.TrackLoadFailed,
        MusicPlayerEvent.Ready,
        MusicPlayerEvent.Stopped,
        MusicPlayerEvent.ConnectFailed,
        MusicPlayerEvent.PlayerNotFound {

    String playerId();
    String requestId();

    record TrackStart(String playerId, String requestId, String guildId, TrackMetadata track) implements MusicPlayerEvent {}
    record TrackEnd(String playerId, String requestId, String guildId, TrackMetadata track) implements MusicPlayerEvent {}
    record TrackError(String playerId, String requestId, String guildId, TrackMetadata track) implements MusicPlayerEvent {}
    record QueueEmpty(String playerId, String requestId, String guildId) implements MusicPlayerEvent {}
    record Paused(String playerId, String requestId, String guildId) implements MusicPlayerEvent {}
    record Resumed(String playerId, String requestId, String guildId) implements MusicPlayerEvent {}
    record NothingToGoBack(String playerId, String requestId, String guildId) implements MusicPlayerEvent {}
    record TrackNotFound(String playerId, String requestId, String guildId, String query) implements MusicPlayerEvent {}
    record TrackLoadFailed(String playerId, String requestId, String guildId, String reason) implements MusicPlayerEvent {}
    record Ready(String playerId, String requestId) implements MusicPlayerEvent {}
    record Stopped(String playerId, String requestId) implements MusicPlayerEvent {}
    record ConnectFailed(String playerId, String requestId, String reason) implements MusicPlayerEvent {}
    record PlayerNotFound(String playerId, String requestId, String guildId) implements MusicPlayerEvent {}
}
