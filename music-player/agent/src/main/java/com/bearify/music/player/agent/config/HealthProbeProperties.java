package com.bearify.music.player.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("music-player.health")
public record HealthProbeProperties(
        // an official, years-old upload that is unlikely to vanish; swap it if it ever does
        @DefaultValue("https://www.youtube.com/watch?v=dQw4w9WgXcQ") String probeUrl,
        // covers loading the track plus the first audio frame; keep it below the controller's bearify.health.timeout (30s)
        @DefaultValue("20s") Duration probeTimeout) {
}
