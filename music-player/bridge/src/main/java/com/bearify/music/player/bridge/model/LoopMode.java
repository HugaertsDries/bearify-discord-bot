package com.bearify.music.player.bridge.model;

/** What the bear plays when a track ends; {@link #next()} cycles OFF → QUEUE → TRACK → OFF. */
public enum LoopMode {
    OFF,
    QUEUE,
    TRACK;

    public LoopMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
