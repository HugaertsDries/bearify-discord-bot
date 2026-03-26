package com.bearify.music.player.bridge.events;

import com.bearify.music.player.bridge.model.Request;
import com.bearify.music.player.bridge.model.TrackRequest;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MusicPlayerInteraction.Play.class, name = "play"),
        @JsonSubTypes.Type(value = MusicPlayerInteraction.TogglePause.class, name = "toggle_pause"),
        @JsonSubTypes.Type(value = MusicPlayerInteraction.Next.class, name = "next"),
        @JsonSubTypes.Type(value = MusicPlayerInteraction.Previous.class, name = "previous"),
        @JsonSubTypes.Type(value = MusicPlayerInteraction.Rewind.class, name = "rewind"),
        @JsonSubTypes.Type(value = MusicPlayerInteraction.Forward.class, name = "forward"),
        @JsonSubTypes.Type(value = MusicPlayerInteraction.Stop.class, name = "stop"),
        @JsonSubTypes.Type(value = MusicPlayerInteraction.Connect.class, name = "connect"),
        @JsonSubTypes.Type(value = MusicPlayerInteraction.Clear.class, name = "clear")
})
public sealed interface MusicPlayerInteraction permits
        MusicPlayerInteraction.Play,
        MusicPlayerInteraction.TogglePause,
        MusicPlayerInteraction.Next,
        MusicPlayerInteraction.Previous,
        MusicPlayerInteraction.Rewind,
        MusicPlayerInteraction.Forward,
        MusicPlayerInteraction.Stop,
        MusicPlayerInteraction.Connect,
        MusicPlayerInteraction.Clear {

    String playerId();
    String requestId();

    record Play(String playerId, String requestId, String guildId, TrackRequest trackRequest) implements MusicPlayerInteraction {}
    record TogglePause(String playerId, Request request, String guildId) implements MusicPlayerInteraction {
        @Override public String requestId() { return request.id(); }
    }
    record Next(String playerId, Request request, String guildId) implements MusicPlayerInteraction {
        @Override public String requestId() { return request.id(); }
    }
    record Previous(String playerId, Request request, String guildId) implements MusicPlayerInteraction {
        @Override public String requestId() { return request.id(); }
    }
    /** seekMs of 0 means use the player's configured default. */
    record Rewind(String playerId, Request request, String guildId, long seekMs) implements MusicPlayerInteraction {
        @Override public String requestId() { return request.id(); }
    }
    /** seekMs of 0 means use the player's configured default. */
    record Forward(String playerId, Request request, String guildId, long seekMs) implements MusicPlayerInteraction {
        @Override public String requestId() { return request.id(); }
    }
    record Stop(String playerId, String requestId, String guildId) implements MusicPlayerInteraction {}
    record Connect(String playerId, String requestId, String voiceChannelId, String guildId) implements MusicPlayerInteraction {}
    record Clear(String playerId, Request request, String guildId) implements MusicPlayerInteraction {
        @Override public String requestId() { return request.id(); }
    }
}
