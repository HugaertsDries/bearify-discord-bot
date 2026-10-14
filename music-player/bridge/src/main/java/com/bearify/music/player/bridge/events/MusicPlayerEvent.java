package com.bearify.music.player.bridge.events;

import com.bearify.music.player.bridge.model.LoopMode;
import com.bearify.music.player.bridge.model.Request;
import com.bearify.music.player.bridge.model.StopReason;
import com.bearify.music.player.bridge.model.TrackMetadata;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Optional;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MusicPlayerEvent.TrackStart.class, name = "track_start"),
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
        @JsonSubTypes.Type(value = MusicPlayerEvent.SearchResults.class, name = "search_results"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Ready.class, name = "player_ready"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Stopped.class, name = "player_stopped"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.ConnectFailed.class, name = "connect_failed"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.PlayerNotFound.class, name = "player_not_found"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Restarted.class, name = "restarted"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.NothingPlaying.class, name = "nothing_playing"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.TrackQueued.class, name = "track_queued"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.HealthCheckResult.class, name = "health_check_result"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.AutoPaused.class, name = "auto_paused"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.AutoResumed.class, name = "auto_resumed"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.QueueSnapshot.class, name = "queue_snapshot"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.NotSeekable.class, name = "not_seekable"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Removed.class, name = "removed"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.TrackNotInQueue.class, name = "track_not_in_queue"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Shuffled.class, name = "shuffled"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.VolumeChanged.class, name = "volume_changed"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.LoopModeChanged.class, name = "loop_mode_changed"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.Jumped.class, name = "jumped"),
        @JsonSubTypes.Type(value = MusicPlayerEvent.JumpOutOfRange.class, name = "jump_out_of_range"),
})
public sealed interface MusicPlayerEvent permits
        MusicPlayerEvent.TrackStart,
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
        MusicPlayerEvent.SearchResults,
        MusicPlayerEvent.Ready,
        MusicPlayerEvent.Stopped,
        MusicPlayerEvent.ConnectFailed,
        MusicPlayerEvent.PlayerNotFound,
        MusicPlayerEvent.Restarted,
        MusicPlayerEvent.NothingPlaying,
        MusicPlayerEvent.TrackQueued,
        MusicPlayerEvent.HealthCheckResult,
        MusicPlayerEvent.AutoPaused,
        MusicPlayerEvent.AutoResumed,
        MusicPlayerEvent.QueueSnapshot,
        MusicPlayerEvent.NotSeekable,
        MusicPlayerEvent.Removed,
        MusicPlayerEvent.TrackNotInQueue,
        MusicPlayerEvent.Shuffled,
        MusicPlayerEvent.VolumeChanged,
        MusicPlayerEvent.LoopModeChanged,
        MusicPlayerEvent.Jumped,
        MusicPlayerEvent.JumpOutOfRange {

    /** How many upcoming tracks the bear sends as {@code upNext}; the controller's Show button lists the rest. */
    int UP_NEXT_SIZE = 3;

    String playerId();
    String requestId();
    String guildId();

    /**
     * {@code upNext} is the start of the queue; {@code queueSize} and {@code queueDurationMs} (live streams excluded)
     * cover the whole queue, and are 0 when an older bear sent the event (boxed so their absence reads as 0, not an error).
     * {@code volume} is the bear's current volume, 100 (LavaPlayer's default) when an older bear sent the event.
     * {@code loopMode} is the bear's current loop, OFF when an older bear sent the event.
     */
    record TrackStart(String playerId, Request request, String guildId, TrackMetadata track, List<TrackMetadata> upNext, Integer queueSize, Long queueDurationMs, Integer volume, LoopMode loopMode) implements MusicPlayerEvent {
        public TrackStart {
            upNext = upNext != null ? List.copyOf(upNext) : List.of();
            queueSize = queueSize != null ? queueSize : 0;
            queueDurationMs = queueDurationMs != null ? queueDurationMs : 0L;
            volume = volume != null ? volume : 100;
            loopMode = loopMode != null ? loopMode : LoopMode.OFF;
        }
        @Override public String requestId() { return request.id(); }
    }
    record TrackError(String playerId, String requestId, String guildId, TrackMetadata track) implements MusicPlayerEvent {}
    /** Same queue fields as {@link TrackStart}. */
    record QueueUpdated(String playerId, String requestId, String guildId, List<TrackMetadata> upNext, Integer queueSize, Long queueDurationMs) implements MusicPlayerEvent {
        public QueueUpdated {
            upNext = upNext != null ? List.copyOf(upNext) : List.of();
            queueSize = queueSize != null ? queueSize : 0;
            queueDurationMs = queueDurationMs != null ? queueDurationMs : 0L;
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
    record SearchResults(String playerId, String requestId, String guildId, List<TrackMetadata> tracks) implements MusicPlayerEvent {
        public SearchResults {
            tracks = tracks != null ? List.copyOf(tracks) : List.of();
        }
    }
    record Ready(String playerId, String requestId, String guildId) implements MusicPlayerEvent {}
    record Stopped(String playerId, String requestId, String guildId, StopReason reason) implements MusicPlayerEvent {
        public Stopped {
            // an older agent sends no reason
            reason = reason != null ? reason : StopReason.REQUESTED;
        }
    }
    record ConnectFailed(String playerId, String requestId, String guildId, String reason) implements MusicPlayerEvent {}
    record PlayerNotFound(String playerId, String requestId, String guildId) implements MusicPlayerEvent {}
    record Restarted(String playerId, Request request, String guildId) implements MusicPlayerEvent {
        @Override public String requestId() { return request.id(); }
    }
    record NothingPlaying(String playerId, String requestId, String guildId) implements MusicPlayerEvent {}
    record TrackQueued(String playerId, String requestId, String guildId, TrackMetadata track, int queuedCount) implements MusicPlayerEvent {}
    record HealthCheckResult(String playerId, String requestId, boolean ok, Optional<String> reason) implements MusicPlayerEvent {
        @Override public String guildId() { return ""; }
    }
    record AutoPaused(String playerId, String requestId, String guildId) implements MusicPlayerEvent {}
    record AutoResumed(String playerId, String requestId, String guildId) implements MusicPlayerEvent {}
    /** The whole queue, not just the next three tracks the player message shows. */
    record QueueSnapshot(String playerId, String requestId, String guildId, Optional<TrackMetadata> current, List<TrackMetadata> upcoming) implements MusicPlayerEvent {
        public QueueSnapshot {
            upcoming = upcoming != null ? List.copyOf(upcoming) : List.of();
        }
    }
    /** A rewind, forward or jump on a track that can't seek, such as a live stream. */
    record NotSeekable(String playerId, String requestId, String guildId) implements MusicPlayerEvent {}
    /** Same queue fields as {@link QueueUpdated}, so the player message keeps counting what's further up the trail. */
    record Removed(String playerId, Request request, String guildId, TrackMetadata track, List<TrackMetadata> upNext, int queueSize, long queueDurationMs) implements MusicPlayerEvent {
        public Removed {
            upNext = upNext != null ? List.copyOf(upNext) : List.of();
        }
        @Override public String requestId() { return request.id(); }
    }
    /** A remove whose track already left the queue. */
    record TrackNotInQueue(String playerId, String requestId, String guildId) implements MusicPlayerEvent {}
    /**
     * {@code count} is the whole queue, 0 when there was nothing to shuffle (not {@link QueueEmpty}: that removes the player message).
     * A shuffle leaves the queue's duration as it was.
     */
    record Shuffled(String playerId, Request request, String guildId, int count, List<TrackMetadata> upNext) implements MusicPlayerEvent {
        public Shuffled {
            upNext = upNext != null ? List.copyOf(upNext) : List.of();
        }
        @Override public String requestId() { return request.id(); }
    }

    record VolumeChanged(String playerId, Request request, String guildId, int volume) implements MusicPlayerEvent {
        @Override public String requestId() { return request.id(); }
    }
    record LoopModeChanged(String playerId, Request request, String guildId, LoopMode mode) implements MusicPlayerEvent {
        @Override public String requestId() { return request.id(); }
    }

    record Jumped(String playerId, Request request, String guildId, long positionMs) implements MusicPlayerEvent {
        @Override public String requestId() { return request.id(); }
    }
    /** A jump to or past the end of the playing track. */
    record JumpOutOfRange(String playerId, String requestId, String guildId, long durationMs) implements MusicPlayerEvent {}
}
