package com.bearify.controller.music.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("music-player.pool")
public record MusicPlayerPoolProperties(
        @DefaultValue("5s")  Duration connectRequestTTL,
        @DefaultValue("30s") Duration interactionTimeout) {
}
