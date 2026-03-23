package com.bearify.music.player.agent.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("music-player")
public record PlayerProperties(
        @DefaultValue("3s")  Duration previousRestartThreshold,
        @DefaultValue("10s") Duration seekShortDefault,
        @DefaultValue("30s") Duration seekLongDefault,
        @DefaultValue("5m")  Duration seekTrackLengthThreshold,
        @DefaultValue Assignment assignment) {

    public record Assignment(
            @DefaultValue("30s") Duration ttl,
            @DefaultValue("10s") Duration heartbeatInterval) {

        @AssertTrue(message = "music-player.assignment.ttl must be greater than music-player.assignment.heartbeat-interval")
        public boolean isTtlGreaterThanHeartbeatInterval() {
            return ttl.compareTo(heartbeatInterval) > 0;
        }
    }
}
