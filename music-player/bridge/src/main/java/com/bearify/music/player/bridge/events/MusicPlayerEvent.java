package com.bearify.music.player.bridge.events;

import com.bearify.music.player.bridge.model.Request;
import com.bearify.music.player.bridge.model.TrackMetadata;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MusicPlayerEvent.TrackStart.class, name = "track_start"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.TrackEnd.class, name = "track_end"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.TrackError.class, name = "track_error"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.QueueUpdated.class, name = "queue_updated"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.QueueEmpty.class, name = "queue_empty"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Paused.class, name = "paused"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Resumed.class, name = "resumed"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Skipped.class, name = "skipped"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.WentBack.class, name = "went_back"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Rewound.class, name = "rewound"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Forwarded.class, name = "forwarded"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Cleared.class, name = "cleared"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.NothingToAdvance.class, name = "nothing_to_advance"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.NothingToGoBack.class, name = "nothing_to_go_back"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.TrackNotFound.class, name = "track_not_found"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.TrackLoadFailed.class, name = "track_load_failed"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Ready.class, name = "player_ready"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Stopped.class, name = "player_stopped"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.ConnectFailed.class, name = "connect_failed"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.PlayerNotFound.class, name = "player_not_found"),
})
public sealed interface MusicPlayerEvent permits
        MusicPlayerEvent.TrackStart,
        MusicPlayerEvent.TrackEnd,
        MusicPlayerEvent.TrackError,
        MusicPlayerEvent.QueueUpdated,
        MusicPlayerEvent.QueueEmpty,
        MusicPlayerEvent.Paused,
        MusicPlayerEvent.Resumed,
        MusicPlayerEvent.Skipped,
        MusicPlayerEvent.WentBack,
        MusicPlayerEvent.Rewound,
        MusicPlayerEvent.Forwarded,
        MusicPlayerEvent.Cleared,
        MusicPlayerEvent.NothingToAdvance,
        MusicPlayerEvent.NothingToGoBack,
        MusicPlayerEvent.TrackNotFound,
        MusicPlayerEvent.TrackLoadFailed,
        MusicPlayerEvent.Ready,
        MusicPlayerEvent.Stopped,
        MusicPlayerEvent.ConnectFailed,
        MusicPlayerEvent.PlayerNotFound {

    String playerId();
    String requestId();

    record TrackStart(String playerId, Request request, String guildId, TrackMetadata track, List<TrackMetadata> upNext) implements MusicPlayerEvent {
        public TrackStart {
            upNext = upNext != null ? List.copyOf(upNext) : List.of();
        }
        @Override public String requestId() { return request.id(); }
    }
    record TrackEnd(String playerId, String requestId, String guildId, TrackMetadata track) implements MusicPlayerEvent {}
    record TrackError(String playerId, String requestId, String guildId, TrackMetadata track) implements MusicPlayerEvent {}
    record QueueUpdated(String playerId, String requestId, String guildId, List<TrackMetadata> upNext) implements MusicPlayerEvent {
        public QueueUpdated {
            upNext = upNext != null ? List.copyOf(upNext) : List.of();
        }
    }
    record QueueEmpty(String playerId, String requestId, String guildId) implements MusicPlayerEvent {}
    record Paused(String playerId, Request request, String guildId) implements MusicPlayerEvent {
        @Override public String requestId() { return request.id(); }
    }
    record Resumed(String playerId, Request request, String guildId) implements MusicPlayerEvent {
        @Override public String requestId() { return request.id(); }
    }
    record Skipped(String playerId, Request request, String guildId) implements MusicPlayerEvent {
        @Override public String requestId() { return request.id(); }
    }
    record WentBack(String playerId, Request request, String guildId) implements MusicPlayerEvent {
        @Override public String requestId() { return request.id(); }
    }
    record Rewound(String playerId, Request request, String guildId, long seekMs) implements MusicPlayerEvent {
        @Override public String requestId() { return request.id(); }
    }
    record Forwarded(String playerId, Request request, String guildId, long seekMs) implements MusicPlayerEvent {
        @Override public String requestId() { return request.id(); }
    }
    record Cleared(String playerId, Request request, String guildId, List<TrackMetadata> upNext) implements MusicPlayerEvent {
        public Cleared {
            upNext = upNext != null ? List.copyOf(upNext) : List.of();
        }
        @Override public String requestId() { return request.id(); }
    }
    record NothingToAdvance(String playerId, String requestId, String guildId) implements MusicPlayerEvent {}
    record NothingToGoBack(String playerId, String requestId, String guildId) implements MusicPlayerEvent {}
    record TrackNotFound(String playerId, String requestId, String guildId, String query) implements MusicPlayerEvent {}
    record TrackLoadFailed(String playerId, String requestId, String guildId, String reason) implements MusicPlayerEvent {}
    record Ready(String playerId, String requestId) implements MusicPlayerEvent {}
    record Stopped(String playerId, String requestId, String guildId) implements MusicPlayerEvent {}
    record ConnectFailed(String playerId, String requestId, String reason) implements MusicPlayerEvent {}
    record PlayerNotFound(String playerId, String requestId, String guildId) implements MusicPlayerEvent {}
}
