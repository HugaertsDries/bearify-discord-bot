package com.bearify.shared.events;

import com.bearify.shared.model.Track;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PlayerCommand.Play.class, name = "play"),
        @JsonSubTypes.Type(value = PlayerCommand.Queue.class, name = "queue"),
        @JsonSubTypes.Type(value = PlayerCommand.Pause.class, name = "pause"),
        @JsonSubTypes.Type(value = PlayerCommand.Resume.class, name = "resume"),
        @JsonSubTypes.Type(value = PlayerCommand.Skip.class, name = "skip"),
        @JsonSubTypes.Type(value = PlayerCommand.Stop.class, name = "stop"),
        @JsonSubTypes.Type(value = PlayerCommand.Connect.class, name = "connect")
})
public sealed interface PlayerCommand permits
        PlayerCommand.Play,
        PlayerCommand.Queue,
        PlayerCommand.Pause,
        PlayerCommand.Resume,
        PlayerCommand.Skip,
        PlayerCommand.Stop,
        PlayerCommand.Connect {

    String playerId();

    record Play(String playerId, Track track) implements PlayerCommand {}
    record Queue(String playerId, Track track) implements PlayerCommand {}
    record Pause(String playerId) implements PlayerCommand {}
    record Resume(String playerId) implements PlayerCommand {}
    record Skip(String playerId) implements PlayerCommand {}
    record Stop(String playerId) implements PlayerCommand {}
    record Connect(String playerId, String voiceChannelId, String guildId) implements PlayerCommand {}
}
