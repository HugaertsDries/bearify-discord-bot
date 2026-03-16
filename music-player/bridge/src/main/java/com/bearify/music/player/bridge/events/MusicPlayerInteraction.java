package com.bearify.music.player.bridge.events;

import com.bearify.music.player.bridge.model.Track;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MusicPlayerInteraction.Play.class, name = "play"),
        @JsonSubTypes.Type(value = MusicPlayerInteraction.Queue.class, name = "queue"),
        @JsonSubTypes.Type(value = MusicPlayerInteraction.Pause.class, name = "pause"),
        @JsonSubTypes.Type(value = MusicPlayerInteraction.Resume.class, name = "resume"),
        @JsonSubTypes.Type(value = MusicPlayerInteraction.Skip.class, name = "skip"),
        @JsonSubTypes.Type(value = MusicPlayerInteraction.Stop.class, name = "stop"),
        @JsonSubTypes.Type(value = MusicPlayerInteraction.Connect.class, name = "connect")
})
public sealed interface MusicPlayerInteraction permits
        MusicPlayerInteraction.Play,
        MusicPlayerInteraction.Queue,
        MusicPlayerInteraction.Pause,
        MusicPlayerInteraction.Resume,
        MusicPlayerInteraction.Skip,
        MusicPlayerInteraction.Stop,
        MusicPlayerInteraction.Connect {

    String playerId();
    String requestId();

    record Play(String playerId, String requestId, Track track) implements MusicPlayerInteraction {}
    record Queue(String playerId, String requestId, Track track) implements MusicPlayerInteraction {}
    record Pause(String playerId, String requestId) implements MusicPlayerInteraction {}
    record Resume(String playerId, String requestId) implements MusicPlayerInteraction {}
    record Skip(String playerId, String requestId) implements MusicPlayerInteraction {}
    record Stop(String playerId, String requestId, String guildId) implements MusicPlayerInteraction {}
    record Connect(String playerId, String requestId, String voiceChannelId, String guildId) implements MusicPlayerInteraction {}
}
