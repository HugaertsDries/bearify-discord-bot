package com.bearify.shared.events;

import com.bearify.shared.model.Track;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PlayerInteraction.Play.class, name = "play"),
        @JsonSubTypes.Type(value = PlayerInteraction.Queue.class, name = "queue"),
        @JsonSubTypes.Type(value = PlayerInteraction.Pause.class, name = "pause"),
        @JsonSubTypes.Type(value = PlayerInteraction.Resume.class, name = "resume"),
        @JsonSubTypes.Type(value = PlayerInteraction.Skip.class, name = "skip"),
        @JsonSubTypes.Type(value = PlayerInteraction.Stop.class, name = "stop"),
        @JsonSubTypes.Type(value = PlayerInteraction.Connect.class, name = "connect")
})
public sealed interface PlayerInteraction permits
        PlayerInteraction.Play,
        PlayerInteraction.Queue,
        PlayerInteraction.Pause,
        PlayerInteraction.Resume,
        PlayerInteraction.Skip,
        PlayerInteraction.Stop,
        PlayerInteraction.Connect {

    String playerId();
    String requestId();

    record Play(String playerId, String requestId, Track track) implements PlayerInteraction {}
    record Queue(String playerId, String requestId, Track track) implements PlayerInteraction {}
    record Pause(String playerId, String requestId) implements PlayerInteraction {}
    record Resume(String playerId, String requestId) implements PlayerInteraction {}
    record Skip(String playerId, String requestId) implements PlayerInteraction {}
    record Stop(String playerId, String requestId) implements PlayerInteraction {}
    record Connect(String playerId, String requestId, String voiceChannelId, String guildId) implements PlayerInteraction {}
}
