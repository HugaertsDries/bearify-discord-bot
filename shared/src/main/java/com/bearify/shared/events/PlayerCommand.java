package com.bearify.shared.events;

import com.bearify.shared.model.Track;

public sealed interface PlayerCommand permits
        PlayerCommand.Play,
        PlayerCommand.Queue,
        PlayerCommand.Pause,
        PlayerCommand.Resume,
        PlayerCommand.Skip,
        PlayerCommand.Stop {

    String playerId();

    record Play(String playerId, Track track) implements PlayerCommand {}
    record Queue(String playerId, Track track) implements PlayerCommand {}
    record Pause(String playerId) implements PlayerCommand {}
    record Resume(String playerId) implements PlayerCommand {}
    record Skip(String playerId) implements PlayerCommand {}
    record Stop(String playerId) implements PlayerCommand {}
}
