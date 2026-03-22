package com.bearify.music.player.bridge.events;

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

    record Play(String playerId, String requestId, String textChannelId, String query, String guildId) implements MusicPlayerInteraction {}
    record TogglePause(String playerId, String requestId, String guildId) implements MusicPlayerInteraction {}
    record Next(String playerId, String requestId, String guildId) implements MusicPlayerInteraction {}
    record Previous(String playerId, String requestId, String guildId) implements MusicPlayerInteraction {}
    /** seekMs of 0 means use the player's configured default. */
    record Rewind(String playerId, String requestId, String guildId, long seekMs) implements MusicPlayerInteraction {}
    /** seekMs of 0 means use the player's configured default. */
    record Forward(String playerId, String requestId, String guildId, long seekMs) implements MusicPlayerInteraction {}
    record Stop(String playerId, String requestId, String guildId) implements MusicPlayerInteraction {}
    record Connect(String playerId, String requestId, String voiceChannelId, String guildId) implements MusicPlayerInteraction {}
    record Clear(String playerId, String requestId, String guildId) implements MusicPlayerInteraction {}
}
