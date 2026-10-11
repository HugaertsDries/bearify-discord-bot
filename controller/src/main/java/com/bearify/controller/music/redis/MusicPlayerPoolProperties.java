package com.bearify.controller.music.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("music-player.pool")
public record MusicPlayerPoolProperties(
        @DefaultValue("5s")  Duration connectRequestTTL,
        @DefaultValue("30s") Duration interactionTimeout,
        // Discord drops autocomplete answers after 3s, so waiting longer is pointless
        @DefaultValue("3s")  Duration searchTimeout,
        // under Discord's 3s autocomplete limit, so a queue-based autocomplete still has time to answer after it
        @DefaultValue("2s")  Duration queueTimeout) {
}
