package com.bearify.music.player.bridge.model;

/** Why a bear left the voice channel. */
public enum StopReason {
    REQUESTED,
    NOBODY_LISTENING,
    IDLE,
    DISCONNECTED,
    /** Set by the controller, never by a bear: the bear vanished without saying goodbye (killed or crashed). */
    LOST
}
