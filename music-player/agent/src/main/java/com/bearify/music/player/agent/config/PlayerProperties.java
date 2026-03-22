package com.bearify.music.player.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("music-player")
public record PlayerProperties(
        @DefaultValue("3s")  Duration previousRestartThreshold,
        @DefaultValue("10s") Duration seekShortDefault,
        @DefaultValue("30s") Duration seekLongDefault,
        @DefaultValue("5m")  Duration seekTrackLengthThreshold) {
}
