package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;

import java.util.Optional;

public interface HealthChecker {

    /** Asks the bears to probe YouTube playback; empty when no bear answered in time. */
    Optional<MusicPlayerEvent.HealthCheckResult> check();
}
