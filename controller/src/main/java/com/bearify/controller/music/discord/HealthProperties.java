package com.bearify.controller.music.discord;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Optional;

@ConfigurationProperties("bearify.health")
public final class HealthProperties {

    private final Optional<String> channelId;
    private final Duration initialDelay;
    private final Duration interval;
    private final Duration brokenInterval;
    private final Duration timeout;
    private final Duration passiveCooldown;

    public HealthProperties(String channelId,
                            // gives freshly deployed bears time to come online before the first check
                            @DefaultValue("2m") Duration initialDelay,
                            @DefaultValue("6h") Duration interval,
                            @DefaultValue("30m") Duration brokenInterval,
                            // must outlast the agent's music-player.health.probe-timeout (20s), which covers loading plus the first frame
                            @DefaultValue("30s") Duration timeout,
                            @DefaultValue("10m") Duration passiveCooldown) {
        this.channelId = Optional.ofNullable(channelId).filter(id -> !id.isBlank());
        this.initialDelay = initialDelay;
        this.interval = interval;
        this.brokenInterval = brokenInterval;
        this.timeout = timeout;
        this.passiveCooldown = passiveCooldown;
    }

    /** The private status channel; the health check is off when it's empty. */
    public Optional<String> channelId() {
        return channelId;
    }

    public Duration initialDelay() {
        return initialDelay;
    }

    public Duration interval() {
        return interval;
    }

    /** How often to check while YouTube is broken or no bear answers. */
    public Duration brokenInterval() {
        return brokenInterval;
    }

    public Duration timeout() {
        return timeout;
    }

    public Duration passiveCooldown() {
        return passiveCooldown;
    }
}
